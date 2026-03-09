require 'ripper'

# Ripper.KEYWORDS was removed in Ruby 3.4.
# Instead, we tokenize candidate words and check if they are NOT :on_ident
candidates = %w[
  BEGIN END __ENCODING__ __FILE__ __LINE__
  alias and begin break case class def defined? do else elsif end ensure
  false for if in module next nil not or redo rescue retry return self
  super then true undef unless until when while yield
]

keywords = candidates.select do |word|
  tokens = Ripper.lex(word)
  # A keyword will NOT be tokenized as :on_ident
  tokens.length == 1 && tokens[0][1] != :on_ident
end

puts keywords.sort
