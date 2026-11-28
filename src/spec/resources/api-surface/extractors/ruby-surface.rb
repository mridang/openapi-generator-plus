# frozen_string_literal: true

# Prints the public API records of the Ruby SDK in /work (see AbstractApiSurfaceSpec).
#
# It requires the gem the way a caller does and walks its constants with Ruby's reflection,
# and reads the documented types and the `@api private` markers from the YARD registry the
# SDK's docs tool writes, so nothing is fetched:
#
#   bundle exec yardoc --no-output --no-stats --quiet
#   bundle exec ruby /tmp/api-surface/ruby-surface.rb petstore_client Petstore::Client
#
# @@S  one line per public declaration: every class and module under the root namespace with
#      its superclass and included modules, every public or protected method with its
#      parameters as `<Owner>#<name>` or `<Owner>.<name>`, every Dry::Struct attribute with its
#      declared type, and every public constant. A line ends with ` [doc ...]` when YARD
#      documents parameter, return or raised types. Superclasses, included modules and
#      documented types are written as given, so a type of another gem shows up by its name
#      (`Faraday::`, `Dry::`, ...). A class, module or method tagged `@api private` (or
#      `@private`) is left out, the way YARD leaves it out of the docs.
# @@T  every class and module that is not `@api private`, and every `@api private` one a listed
#      line names.
# @@E  the same: every constant of the gem is reachable from its root namespace.
# @@I  an `@api private` class or module that a listed line names, so a caller sees it anyway.

require 'set'
require 'yard'

gem_name = ARGV.fetch(0)
root_name = ARGV.fetch(1)
$LOAD_PATH.unshift(File.join(Dir.pwd, 'lib'))
require gem_name

YARD::Registry.load!('.yardoc')

ROOT = Object.const_get(root_name)
SIMPLE = [String, Symbol, Integer, Float, TrueClass, FalseClass, NilClass].freeze

def private_doc?(path)
  obj = YARD::Registry.at(path)
  return false if obj.nil?

  obj.tags(:api).any? { |t| t.text.to_s.strip == 'private' } || obj.has_tag?(:private)
end

def doc_types(path)
  obj = YARD::Registry.at(path)
  return '' if obj.nil?

  tags = obj.tags
  tags += obj.tags(:overload).flat_map(&:tags) if tags.none? { |t| %w[param return].include?(t.tag_name) }
  parts = []
  tags.each do |t|
    next unless %w[param option return raise yieldparam yieldreturn].include?(t.tag_name)
    next if t.types.nil? || t.types.empty?

    label = t.tag_name == 'param' || t.tag_name == 'yieldparam' ? "#{t.tag_name} #{t.name}" : t.tag_name
    label = "option #{t.name} #{t.pair&.name}" if t.tag_name == 'option'
    types = t.tag_name == 'option' && t.pair ? t.pair.types : t.types
    next if types.nil? || types.empty?

    parts << "#{label}: #{types.join(', ')}"
  end
  parts.empty? ? '' : " [doc #{parts.join('; ')}]"
end

def params_text(method)
  method.parameters.map do |kind, name|
    case kind
    when :req then name.to_s
    when :opt then "#{name} = ?"
    when :rest then name ? "*#{name}" : '*'
    when :keyreq then "#{name}:"
    when :key then "#{name}: ?"
    when :keyrest then name ? "**#{name}" : '**'
    when :block then "&#{name}"
    when :nokey then '**nil'
    else "#{kind} #{name}"
    end
  end.join(', ')
end

def value_text(value)
  case value
  when *SIMPLE then value.inspect
  when Array
    value.all? { |v| SIMPLE.any? { |k| v.is_a?(k) } } ? value.inspect : "Array"
  when Module
    "#{value.class.name} #{value.name}"
  else
    value.class.name || value.class.to_s
  end
end

signatures = Set.new
records = Set.new
internal = {}
modules = []

walk = lambda do |mod|
  modules << mod
  mod.constants(false).sort.each do |name|
    value = mod.const_get(name, false)
    next unless value.is_a?(Module)
    next unless value.name == "#{mod.name}::#{name}"
    # A module another gem builds and the SDK only assigns to a constant (Types::Builder =
    # Dry.Types()) is that gem's API: it is listed as a constant, not walked.
    next unless YARD::Registry.at(value.name)&.type.then { |t| %i[class module].include?(t) }

    walk.call(value)
  end
end
walk.call(ROOT)

modules.sort_by!(&:name)
modules.each do |mod|
  path = mod.name
  if private_doc?(path)
    internal[path] = true
    next
  end
  records << "@@T #{path}"
  records << "@@E #{path}"
  if mod.is_a?(Class)
    line = "class #{path}"
    line += " < #{mod.superclass.name}" if mod.superclass && mod.superclass != Object
    included = mod.ancestors.take_while { |a| a != mod.superclass }.reject { |a| a == mod || a.is_a?(Class) }
  else
    line = "module #{path}"
    included = mod.ancestors.reject { |a| a == mod }
  end
  names = included.map { |m| m.name || m.to_s }.reject { |n| n.start_with?('#<') }.sort
  line += " include #{names.join(', ')}" unless names.empty?
  signatures << line

  attributes = {}
  if mod.is_a?(Class) && defined?(Dry::Struct) && mod < Dry::Struct && mod.respond_to?(:schema)
    declared = mod.const_defined?(:OPENAPI_TYPES, false) ? mod.const_get(:OPENAPI_TYPES, false) : {}
    mod.schema.keys.each do |key|
      type = declared[key.name] || declared[key.name.to_sym] || 'untyped'
      required = key.required? ? '' : '?'
      attributes[key.name.to_sym] = true
      signatures << "#{path}#attribute #{key.name}#{required}: #{type}"
    end
  end

  mod.constants(false).sort.each do |name|
    value = mod.const_get(name, false)
    next if value.is_a?(Module) && modules.include?(value)

    signatures << "#{path}::#{name} = #{value_text(value)}"
  end

  # The constructor: `initialize` is private by Ruby's rules and reached through `.new`.
  if mod.is_a?(Class) && mod.private_instance_methods(false).include?(:initialize) &&
     !private_doc?("#{path}#initialize")
    init = mod.instance_method(:initialize)
    signatures << "#{path}.new(#{params_text(init)})#{doc_types("#{path}#initialize")}"
  end

  { '#' => [mod.public_instance_methods(false), mod.protected_instance_methods(false)],
    '.' => [mod.singleton_class.public_instance_methods(false), mod.singleton_class.protected_instance_methods(false)] }
    .each do |sep, (publics, protecteds)|
    (publics.map { |m| [m, 'public'] } + protecteds.map { |m| [m, 'protected'] }).sort_by { |m, _| m.to_s }.each do |name, vis|
      next if sep == '#' && attributes.key?(name)

      method = sep == '#' ? mod.instance_method(name) : mod.method(name)
      next unless method.owner == (sep == '#' ? mod : mod.singleton_class)

      member = "#{path}#{sep}#{name}"
      next if private_doc?(member)

      prefix = vis == 'protected' ? 'protected ' : ''
      signatures << "#{prefix}#{member}(#{params_text(method)})#{doc_types(member)}"
    end
  end
end

internal.each_key do |path|
  short = path.split('::').last
  pattern = /(?<![\w:])(?:#{Regexp.escape(path)}|#{Regexp.escape(short)})(?![\w])/
  next unless signatures.any? { |s| s.match?(pattern) }

  records << "@@T #{path}"
  records << "@@E #{path}"
  records << "@@I #{path}"
end

signatures.each { |s| records << "@@S #{s}" }
records.to_a.sort.each { |r| puts r }
