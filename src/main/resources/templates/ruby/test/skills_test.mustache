# frozen_string_literal: true

require 'test_helper'

# Guards that the SKILLS.md examples can be run as written.
describe 'SKILLS' do
  parallelize_me!

  def error_handling_example
    skills = File.read(File.join(File.expand_path('..', File.dirname(__FILE__)), 'SKILLS.md'))
    section = skills.split('## Error Handling', 2).last.to_s
    section.match(/```ruby\n(.*?)```/m)&.captures&.first.to_s
  end

  it 'defines the Error Handling example as a method taking the operation arguments' do
    _(error_handling_example).must_include 'def add_pet_or_report(client, pet)'
    _(error_handling_example).must_include '  client.pet.add_pet(pet)'
  end

  it 'leaves no undefined variable in the Error Handling example' do
    _(error_handling_example).wont_include '(request)'
  end

  it 'parses the Error Handling example' do
    _(RubyVM::InstructionSequence.compile(error_handling_example)).wont_be_nil
  end
end
