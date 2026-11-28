// Prints the public API records of the Dart SDK in /work (see AbstractApiSurfaceSpec).
//
// It reads the package's libraries with the analyzer that the SDK's dev dependencies
// already resolve (the `test` package depends on it), so nothing is fetched:
//
//   dart run --packages=.dart_tool/package_config.json /tmp/api-surface/dart-surface.dart
//
// @@S  every public declaration exported from the barrel lib/<package>.dart, one line each
//      as `<Owner>::<declaration>` (a top-level declaration has no owner part). A line whose
//      declaration names a type of another package (not the Dart SDK) ends with
//      ` [uses <package>:<Type>, ...]`, so the leak check can tell `package:http`'s `Client`
//      from the SDK's own `Client`. A declaration of another package that the barrel
//      re-exports is listed as `export <name> from <uri>`.
// @@T  every public type a caller can see: the types the barrel exports and the types under
//      lib/src that an exported declaration names (lib/src is private to the package by the
//      Dart convention, so a type there that no exported declaration names is not public).
// @@E  every type the barrel exports.
// @@I  an exported type annotated @internal.
import 'dart:io';

import 'package:analyzer/dart/analysis/analysis_context_collection.dart';
import 'package:analyzer/dart/analysis/results.dart';
import 'package:analyzer/dart/element/element.dart';
import 'package:analyzer/dart/element/type.dart';

late final String package;

Future<void> main() async {
  final root = Directory.current.absolute.path;
  package = File('$root/pubspec.yaml')
      .readAsLinesSync()
      .firstWhere((l) => l.startsWith('name:'))
      .substring(5)
      .trim();
  final collection = AnalysisContextCollection(includedPaths: ['$root/lib']);
  final session = collection.contextFor('$root/lib').currentSession;

  Future<LibraryElement?> load(String uri) async {
    final result = await session.getLibraryByUri(uri);
    return result is LibraryElementResult ? result.element : null;
  }

  final out = <String>{};
  final signatures = <String>{};

  final barrel = await load('package:$package/$package.dart');
  if (barrel == null) {
    stderr.writeln('no barrel library package:$package/$package.dart');
    exit(1);
  }
  final exported = barrel.exportNamespace.definedNames2;
  final names = exported.keys.toList()..sort();
  for (final name in names) {
    final element = exported[name]!;
    if (element.isPrivate) continue;
    final from = element.library?.uri.toString() ?? '';
    if (!from.startsWith('package:$package/')) {
      // A declaration of another package re-exported by the barrel: listed by name and
      // origin only, since its members are that package's API.
      signatures.add('export $name from $from');
      continue;
    }
    if (_isType(element)) {
      out.add('@@T $name');
      out.add('@@E $name');
      if (element.metadata.annotations.any((a) => a.isInternal)) {
        out.add('@@I $name');
      }
    }
    signatures.add(_declaration(element));
    if (element is InstanceElement) {
      _members(element, signatures);
    }
  }

  // The public types under lib/ that the barrel does not export but an exported
  // declaration names: a caller sees them and cannot import them.
  final files = Directory('$root/lib')
      .listSync(recursive: true)
      .whereType<File>()
      .where((f) => f.path.endsWith('.dart'))
      .map((f) => f.path.substring('$root/lib/'.length))
      .toList()
    ..sort();
  for (final file in files) {
    final library = await load('package:$package/$file');
    if (library == null || library.uri.toString() != 'package:$package/$file') {
      continue; // a part file
    }
    for (final element in library.children) {
      final name = element.name;
      if (name == null || !element.isPublic || !_isType(element)) continue;
      if (exported[name] == element) continue;
      final word = RegExp('(^|[^A-Za-z0-9_\$])${RegExp.escape(name)}(\$|[^A-Za-z0-9_\$])');
      final outsideSrc = !file.startsWith('src/');
      if (outsideSrc || signatures.any(word.hasMatch)) {
        out.add('@@T $name');
      }
    }
  }

  out.addAll(signatures.map((s) => '@@S $s'));
  for (final line in out.toList()..sort()) {
    stdout.writeln(line);
  }
}

bool _isType(Element element) =>
    element is InterfaceElement ||
    element is TypeAliasElement ||
    element is ExtensionElement;

String _flat(String text) => text.replaceAll(RegExp(r'\s+'), ' ').trim();

String _declaration(Element element) {
  if (element is GetterElement && element.variable is TopLevelVariableElement) {
    final variable = element.variable as TopLevelVariableElement;
    return _withUses(_variable(variable), [variable.type]);
  }
  return _withUses(_flat(element.displayString()), _typesOf(element));
}

String _variable(PropertyInducingElement variable) {
  final modifiers = [
    if (variable.isStatic && variable is FieldElement) 'static',
    if (variable.isConst) 'const' else if (variable.isFinal) 'final',
  ];
  return [...modifiers, variable.type.getDisplayString(), variable.name ?? '']
      .join(' ');
}

/// The types an element's declaration names.
List<DartType> _typesOf(Element element) {
  if (element is InterfaceElement) {
    return [
      if (element.supertype != null) element.supertype!,
      ...element.mixins,
      ...element.interfaces,
    ];
  }
  if (element is TypeAliasElement) return [element.aliasedType];
  if (element is ExtensionElement) return [element.extendedType];
  if (element is ExecutableElement) return [element.type];
  if (element is VariableElement) return [element.type];
  return const [];
}

/// Appends the types of other packages (not the Dart SDK) that [types] name.
String _withUses(String declaration, List<DartType> types) {
  final foreign = <String>{};
  final seen = <DartType>{};
  void walk(DartType? type) {
    if (type == null || !seen.add(type)) return;
    final alias = type.alias;
    if (alias != null) {
      _note(alias.element, foreign);
      alias.typeArguments.forEach(walk);
    }
    if (type is InterfaceType) {
      _note(type.element, foreign);
      type.typeArguments.forEach(walk);
    } else if (type is FunctionType) {
      walk(type.returnType);
      for (final p in type.formalParameters) {
        walk(p.type);
      }
    } else if (type is RecordType) {
      for (final f in type.positionalFields) {
        walk(f.type);
      }
      for (final f in type.namedFields) {
        walk(f.type);
      }
    }
  }

  types.forEach(walk);
  if (foreign.isEmpty) return declaration;
  return '$declaration [uses ${(foreign.toList()..sort()).join(', ')}]';
}

void _note(Element element, Set<String> foreign) {
  final uri = element.library?.uri;
  if (uri == null || uri.scheme != 'package') return;
  final owner = uri.pathSegments.first;
  if (owner != package) foreign.add('$owner:${element.name}');
}

void _members(InstanceElement owner, Set<String> out) {
  final name = owner.name ?? '';
  void add(String declaration, List<DartType> types) =>
      out.add('$name::${_withUses(_flat(declaration), types)}');

  if (owner is EnumElement) {
    for (final constant in owner.constants) {
      if (constant.isPublic) add('enum value ${constant.name}', const []);
    }
  }
  if (owner is InterfaceElement) {
    for (final constructor in owner.constructors) {
      if (constructor.isPublic) {
        add(constructor.displayString(), [constructor.type]);
      }
    }
  }
  for (final field in owner.fields) {
    if (!field.isPublic || field.isEnumConstant || field.isOriginEnumValues) continue;
    if (!field.isOriginDeclaration) continue; // implied by a getter or setter
    add(_variable(field), [field.type]);
  }
  for (final getter in owner.getters) {
    if (getter.isPublic && getter.isOriginDeclaration) {
      add(getter.displayString(), [getter.type]);
    }
  }
  for (final setter in owner.setters) {
    if (setter.isPublic && setter.isOriginDeclaration) {
      add(setter.displayString(), [setter.type]);
    }
  }
  for (final method in owner.methods) {
    if (method.isPublic) {
      add('${method.isStatic ? 'static ' : ''}${method.displayString()}',
          [method.type]);
    }
  }
}
