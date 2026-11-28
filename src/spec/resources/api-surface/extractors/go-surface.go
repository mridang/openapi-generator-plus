// Prints the public API records (D180) of the generated Go SDK, read with go/types from the
// compiler's own export data (`go list -export`): every exported constant, variable, function,
// type, struct field and method of the SDK's packages under ./pkg, one signature per line with
// every type qualified by its import path and parameter names left out. Packages with an
// `internal` path segment are not API; a public signature that names one of their types is
// reported as internal-but-exported.
//
// Usage, from a directory outside the module: go run go-surface.go <SDK root>
package main

import (
	"bufio"
	"bytes"
	"encoding/json"
	"fmt"
	"go/importer"
	"go/token"
	"go/types"
	"io"
	"os"
	"os/exec"
	"regexp"
	"sort"
	"strings"
)

type listed struct {
	ImportPath string
	Export     string
	Name       string
}

func main() {
	root := os.Args[1]
	cmd := exec.Command("go", "list", "-e", "-deps", "-export", "-json=ImportPath,Export,Name", "./pkg/...")
	cmd.Dir = root
	cmd.Stderr = os.Stderr
	out, err := cmd.Output()
	if err != nil {
		fail(err)
	}
	exports := map[string]string{}
	dec := json.NewDecoder(bytes.NewReader(out))
	for {
		var p listed
		if err := dec.Decode(&p); err == io.EOF {
			break
		} else if err != nil {
			fail(err)
		}
		exports[p.ImportPath] = p.Export
	}

	cmd = exec.Command("go", "list", "-f", "{{.ImportPath}}", "./pkg/...")
	cmd.Dir = root
	cmd.Stderr = os.Stderr
	out, err = cmd.Output()
	if err != nil {
		fail(err)
	}
	var targets []string
	scanner := bufio.NewScanner(bytes.NewReader(out))
	for scanner.Scan() {
		if path := strings.TrimSpace(scanner.Text()); path != "" && !isInternal(path) {
			targets = append(targets, path)
		}
	}
	if len(targets) == 0 {
		fail(fmt.Errorf("no package under %s/pkg", root))
	}

	fset := token.NewFileSet()
	imp := importer.ForCompiler(fset, "gc", func(path string) (io.ReadCloser, error) {
		file, ok := exports[path]
		if !ok || file == "" {
			return nil, fmt.Errorf("no export data for %s", path)
		}
		return os.Open(file)
	})

	lines := map[string]bool{}
	for _, path := range targets {
		pkg, err := imp.Import(path)
		if err != nil {
			fail(err)
		}
		for _, name := range pkg.Scope().Names() {
			obj := pkg.Scope().Lookup(name)
			if !obj.Exported() {
				continue
			}
			describe(obj, lines)
		}
	}

	internalRef := regexp.MustCompile(`[\w./-]*/internal(/[\w.-]+)*\.\w+`)
	var sorted []string
	for line := range lines {
		sorted = append(sorted, line)
		if strings.HasPrefix(line, "@@S ") {
			for _, ref := range internalRef.FindAllString(line, -1) {
				sorted = append(sorted, "@@I "+ref)
			}
		}
	}
	sort.Strings(sorted)
	for _, line := range sorted {
		fmt.Println(line)
	}
}

func isInternal(path string) bool {
	for _, seg := range strings.Split(path, "/") {
		if seg == "internal" {
			return true
		}
	}
	return false
}

func qualified(obj types.Object) string {
	return obj.Pkg().Path() + "." + obj.Name()
}

func typ(t types.Type) string {
	return types.TypeString(t, nil)
}

// signature prints a function type without parameter or result names.
func signature(sig *types.Signature) string {
	var b strings.Builder
	if tp := sig.TypeParams(); tp != nil && tp.Len() > 0 {
		b.WriteString(typeParams(tp))
	}
	b.WriteString("(")
	params := sig.Params()
	for i := 0; i < params.Len(); i++ {
		if i > 0 {
			b.WriteString(", ")
		}
		t := params.At(i).Type()
		if sig.Variadic() && i == params.Len()-1 {
			b.WriteString("..." + typ(t.(*types.Slice).Elem()))
		} else {
			b.WriteString(typ(t))
		}
	}
	b.WriteString(")")
	results := sig.Results()
	switch results.Len() {
	case 0:
	case 1:
		b.WriteString(" " + typ(results.At(0).Type()))
	default:
		b.WriteString(" (")
		for i := 0; i < results.Len(); i++ {
			if i > 0 {
				b.WriteString(", ")
			}
			b.WriteString(typ(results.At(i).Type()))
		}
		b.WriteString(")")
	}
	return b.String()
}

func typeParams(tp *types.TypeParamList) string {
	var parts []string
	for i := 0; i < tp.Len(); i++ {
		p := tp.At(i)
		parts = append(parts, p.Obj().Name()+" "+typ(p.Constraint()))
	}
	return "[" + strings.Join(parts, ", ") + "]"
}

func describe(obj types.Object, lines map[string]bool) {
	name := qualified(obj)
	switch o := obj.(type) {
	case *types.Const:
		lines["@@S const "+name+" "+typ(o.Type())+" = "+o.Val().ExactString()] = true
	case *types.Var:
		lines["@@S var "+name+" "+typ(o.Type())] = true
	case *types.Func:
		lines["@@S func "+name+signature(o.Type().(*types.Signature))] = true
	case *types.TypeName:
		lines["@@T "+name] = true
		lines["@@E "+name] = true
		if o.IsAlias() {
			lines["@@S type "+name+" = "+typ(types.Unalias(o.Type()))] = true
			return
		}
		named, ok := o.Type().(*types.Named)
		if !ok {
			return
		}
		params := ""
		if tp := named.TypeParams(); tp != nil && tp.Len() > 0 {
			params = typeParams(tp)
		}
		switch u := named.Underlying().(type) {
		case *types.Struct:
			lines["@@S type "+name+params+" struct"] = true
			for i := 0; i < u.NumFields(); i++ {
				f := u.Field(i)
				if !f.Exported() {
					continue
				}
				embedded := ""
				if f.Embedded() {
					embedded = " embedded"
				}
				tag := ""
				if t := u.Tag(i); t != "" {
					tag = " `" + t + "`"
				}
				lines["@@S field "+name+"."+f.Name()+" "+typ(f.Type())+embedded+tag] = true
			}
		case *types.Interface:
			lines["@@S type "+name+params+" interface"] = true
			for i := 0; i < u.NumEmbeddeds(); i++ {
				lines["@@S embeds "+name+" "+typ(u.EmbeddedType(i))] = true
			}
			for i := 0; i < u.NumExplicitMethods(); i++ {
				m := u.ExplicitMethod(i)
				if !m.Exported() {
					lines["@@S type "+name+" has unexported methods"] = true
					continue
				}
				lines["@@S method "+name+"."+m.Name()+signature(m.Type().(*types.Signature))] = true
			}
		default:
			lines["@@S type "+name+params+" "+typ(u)] = true
		}
		for i := 0; i < named.NumMethods(); i++ {
			m := named.Method(i)
			if !m.Exported() {
				continue
			}
			sig := m.Type().(*types.Signature)
			recv := "(" + name + ")"
			if _, ptr := sig.Recv().Type().(*types.Pointer); ptr {
				recv = "(*" + name + ")"
			}
			lines["@@S method "+recv+"."+m.Name()+signature(sig)] = true
		}
	}
}

func fail(err error) {
	fmt.Fprintln(os.Stderr, "go-surface:", err)
	os.Exit(1)
}
