package main

import (
	"fmt"
	"go/token"
)

func main() {
	for i := token.BREAK; i <= token.VAR; i++ {
		if token.IsKeyword(i.String()) {
			fmt.Println(i.String())
		}
	}
	predeclared := []string{
		"append", "bool", "byte", "cap", "close", "complex",
		"complex64", "complex128", "copy", "delete", "error",
		"false", "float32", "float64", "imag", "int", "int8",
		"int16", "int32", "int64", "iota", "len", "make",
		"new", "nil", "panic", "print", "println", "real",
		"recover", "rune", "string", "true", "uint", "uint8",
		"uint16", "uint32", "uint64", "uintptr",
	}
	for _, id := range predeclared {
		fmt.Println(id)
	}
}
