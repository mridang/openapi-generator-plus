package petstore

import (
	"encoding/json"
	"os"
	"testing"

	"petstore/bench/mirror"
	"petstore/pkg/models"
)

var payloads = map[string][]byte{}

func load(name string) []byte {
	data, err := os.ReadFile("/fixtures/" + name + ".json")
	if err != nil {
		panic(err)
	}
	return data
}

func init() {
	for _, name := range []string{"small", "medium", "large"} {
		payloads[name] = load(name)
	}
}

type target struct {
	name  string
	fresh func() any
}

var targets = []target{
	{"small", func() any { return &models.Tag{} }},
	{"medium", func() any { return &models.Pet{} }},
	{"large", func() any { return &[]models.Pet{} }},
}

var mirrorTargets = []target{
	{"small", func() any { return &mirror.Tag{} }},
	{"medium", func() any { return &mirror.Pet{} }},
	{"large", func() any { return &mirror.PetList{} }},
}

func runDecode(b *testing.B, name string, ts []target, decode func([]byte, any) error) {
	for _, t := range ts {
		data := payloads[t.name]
		b.Run(name+"/"+t.name+"/decode", func(b *testing.B) {
			b.ReportAllocs()
			b.SetBytes(int64(len(data)))
			for i := 0; i < b.N; i++ {
				if err := decode(data, t.fresh()); err != nil {
					b.Fatal(err)
				}
			}
		})
	}
}

func runEncode(b *testing.B, name string, ts []target, decode func([]byte, any) error, encode func(any) ([]byte, error)) {
	for _, t := range ts {
		data := payloads[t.name]
		value := t.fresh()
		if err := decode(data, value); err != nil {
			b.Fatal(err)
		}
		b.Run(name+"/"+t.name+"/encode", func(b *testing.B) {
			b.ReportAllocs()
			b.SetBytes(int64(len(data)))
			for i := 0; i < b.N; i++ {
				if _, err := encode(value); err != nil {
					b.Fatal(err)
				}
			}
		})
	}
}

func BenchmarkGenerated(b *testing.B) {
	runDecode(b, "generated", targets, deserialize)
	runEncode(b, "generated", targets, deserialize, serialize)
}

func BenchmarkStdlibJSON(b *testing.B) {
	runDecode(b, "stdlib", targets, json.Unmarshal)
	runEncode(b, "stdlib", targets, json.Unmarshal, func(v any) ([]byte, error) { return json.Marshal(v) })
}

func BenchmarkEasyjson(b *testing.B) {
	dec := func(data []byte, v any) error { return v.(interface{ UnmarshalJSON([]byte) error }).UnmarshalJSON(data) }
	enc := func(v any) ([]byte, error) { return v.(json.Marshaler).MarshalJSON() }
	runDecode(b, "easyjson", mirrorTargets, dec)
	runEncode(b, "easyjson", mirrorTargets, dec, enc)
}
