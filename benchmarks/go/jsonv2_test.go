//go:build goexperiment.jsonv2

package petstore

import (
	jsonv2 "encoding/json/v2"
	"testing"
)

func BenchmarkJSONv2(b *testing.B) {
	runDecode(b, "jsonv2", targets, func(d []byte, v any) error { return jsonv2.Unmarshal(d, v) })
	runEncode(b, "jsonv2", targets,
		func(d []byte, v any) error { return jsonv2.Unmarshal(d, v) },
		func(v any) ([]byte, error) { return jsonv2.Marshal(v) })
}
