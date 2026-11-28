package com.example.petstore;

import com.example.petstore.models.Category;
import com.example.petstore.models.Pet;
import com.example.petstore.models.Tag;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.afterburner.AfterburnerModule;
import com.fasterxml.jackson.module.blackbird.BlackbirdModule;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Runs inside the generated SDK's package so it can reach the package-private mapper factory.
 * Prints rows: lang impl model op ns_per_op bytes_per_op allocs_per_op (allocs are not available on the JVM).
 */
public final class Bench {
  private static final JsonFactory FACTORY = new JsonFactory();
  private static final com.sun.management.ThreadMXBean MX =
      (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
  private static Object sink;

  interface Codec {
    Object decode(String model, String json) throws Exception;

    String encode(String model, Object value) throws Exception;
  }

  static Type typeOf(String model) {
    switch (model) {
      case "small":
        return Tag.class;
      case "medium":
        return Pet.class;
      default:
        return new TypeReference<List<Pet>>() {}.getType();
    }
  }

  static Codec mapperCodec(ObjectMapper mapper) {
    return new Codec() {
      public Object decode(String model, String json) throws Exception {
        return mapper.readValue(json, mapper.getTypeFactory().constructType(typeOf(model)));
      }

      public String encode(String model, Object value) throws Exception {
        return mapper.writeValueAsString(value);
      }
    };
  }

  static Codec generatedCodec() {
    ObjectSerializer serializer = new ObjectSerializer();
    return new Codec() {
      public Object decode(String model, String json) {
        return serializer.deserialize(json, typeOf(model));
      }

      public String encode(String model, Object value) {
        return serializer.serialize(value);
      }
    };
  }

  // ---- hand-written streaming codec: what a generated reflection-free codec would look like ----

  static Tag readTag(JsonParser p) throws IOException {
    Tag.Builder b = Tag.builder();
    while (p.nextToken() == JsonToken.FIELD_NAME) {
      String f = p.currentName();
      p.nextToken();
      switch (f) {
        case "id":
          b.id(p.getLongValue());
          break;
        case "name":
          b.name(p.getText());
          break;
        default:
          p.skipChildren();
      }
    }
    return b.build();
  }

  static Category readCategory(JsonParser p) throws IOException {
    Category.Builder b = Category.builder();
    while (p.nextToken() == JsonToken.FIELD_NAME) {
      String f = p.currentName();
      p.nextToken();
      switch (f) {
        case "id":
          b.id(p.getLongValue());
          break;
        case "name":
          b.name(p.getText());
          break;
        default:
          p.skipChildren();
      }
    }
    return b.build();
  }

  static Pet readPet(JsonParser p) throws IOException {
    Pet.Builder b = Pet.builder();
    while (p.nextToken() == JsonToken.FIELD_NAME) {
      String f = p.currentName();
      p.nextToken();
      switch (f) {
        case "id":
          b.id(p.getLongValue());
          break;
        case "name":
          b.name(p.getText());
          break;
        case "category":
          b.category(readCategory(p));
          break;
        case "photoUrls":
          Set<String> urls = new LinkedHashSet<>();
          while (p.nextToken() != JsonToken.END_ARRAY) {
            urls.add(p.getText());
          }
          b.photoUrls(urls);
          break;
        case "tags":
          List<Tag> tags = new ArrayList<>();
          while (p.nextToken() != JsonToken.END_ARRAY) {
            tags.add(readTag(p));
          }
          b.tags(tags);
          break;
        case "status":
          b.status(Pet.StatusEnum.fromValue(p.getText()));
          break;
        case "homepageUrl":
          b.homepageUrl(URI.create(p.getText()));
          break;
        case "thumbnailRef":
          b.thumbnailRef(p.getText());
          break;
        case "ownerEmail":
          b.ownerEmail(p.getText());
          break;
        case "weightKg":
          b.weightKg(p.getDecimalValue());
          break;
        case "nickname":
          b.nickname(p.getText());
          break;
        default:
          p.skipChildren();
      }
    }
    return b.build();
  }

  static void writeTag(JsonGenerator g, Tag t) throws IOException {
    g.writeStartObject();
    if (t.id != null) g.writeNumberField("id", t.id);
    if (t.name != null) g.writeStringField("name", t.name);
    g.writeEndObject();
  }

  static void writePet(JsonGenerator g, Pet p) throws IOException {
    g.writeStartObject();
    if (p.id != null) g.writeNumberField("id", p.id);
    g.writeStringField("name", p.name);
    if (p.category != null) {
      g.writeObjectFieldStart("category");
      if (p.category.id != null) g.writeNumberField("id", p.category.id);
      if (p.category.name != null) g.writeStringField("name", p.category.name);
      g.writeEndObject();
    }
    g.writeArrayFieldStart("photoUrls");
    for (String u : p.photoUrls) g.writeString(u);
    g.writeEndArray();
    if (p.tags != null) {
      g.writeArrayFieldStart("tags");
      for (Tag t : p.tags) writeTag(g, t);
      g.writeEndArray();
    }
    if (p.status != null) g.writeStringField("status", p.status.getValue());
    if (p.homepageUrl != null) g.writeStringField("homepageUrl", p.homepageUrl.toString());
    if (p.thumbnailRef != null) g.writeStringField("thumbnailRef", p.thumbnailRef);
    if (p.ownerEmail != null) g.writeStringField("ownerEmail", p.ownerEmail);
    if (p.weightKg != null) g.writeNumberField("weightKg", p.weightKg);
    if (p.nickname != null) g.writeStringField("nickname", p.nickname);
    g.writeEndObject();
  }

  static Codec handCodec() {
    return new Codec() {
      public Object decode(String model, String json) throws Exception {
        try (JsonParser p = FACTORY.createParser(json)) {
          p.nextToken();
          switch (model) {
            case "small":
              return readTag(p);
            case "medium":
              return readPet(p);
            default:
              List<Pet> pets = new ArrayList<>();
              while (p.nextToken() != JsonToken.END_ARRAY) pets.add(readPet(p));
              return pets;
          }
        }
      }

      @SuppressWarnings("unchecked")
      public String encode(String model, Object value) throws Exception {
        StringWriter w = new StringWriter();
        try (JsonGenerator g = FACTORY.createGenerator(w)) {
          switch (model) {
            case "small":
              writeTag(g, (Tag) value);
              break;
            case "medium":
              writePet(g, (Pet) value);
              break;
            default:
              g.writeStartArray();
              for (Pet p : (List<Pet>) value) writePet(g, p);
              g.writeEndArray();
          }
        }
        return w.toString();
      }
    };
  }

  /** Sorts every photoUrls array: the set order is not part of the wire contract for this check. */
  static String normalise(ObjectMapper tree, com.fasterxml.jackson.databind.JsonNode node) {
    java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it =
        node.isArray() ? node.elements() : java.util.List.of(node).iterator();
    while (it.hasNext()) {
      com.fasterxml.jackson.databind.JsonNode pet = it.next();
      com.fasterxml.jackson.databind.JsonNode urls = pet.get("photoUrls");
      if (urls != null && urls.isArray()) {
        List<String> sorted = new ArrayList<>();
        urls.forEach(u -> sorted.add(u.asText()));
        java.util.Collections.sort(sorted);
        com.fasterxml.jackson.databind.node.ArrayNode arr = tree.createArrayNode();
        sorted.forEach(arr::add);
        ((com.fasterxml.jackson.databind.node.ObjectNode) pet).set("photoUrls", arr);
      }
    }
    return node.toString();
  }

  // ---- measurement ----

  interface Op {
    Object run() throws Exception;
  }

  static double[] measure(Op op, long targetMs) throws Exception {
    for (int i = 0; i < 3000; i++) sink = op.run();
    long n = 1;
    for (; ; ) {
      long t0 = System.nanoTime();
      for (long i = 0; i < n; i++) sink = op.run();
      long dt = System.nanoTime() - t0;
      if (dt > 50_000_000L) {
        n = Math.max(1, n * targetMs * 1_000_000L / dt);
        break;
      }
      n *= 4;
    }
    long tid = Thread.currentThread().getId();
    long a0 = MX.getThreadAllocatedBytes(tid);
    long t0 = System.nanoTime();
    for (long i = 0; i < n; i++) sink = op.run();
    long dt = System.nanoTime() - t0;
    long a1 = MX.getThreadAllocatedBytes(tid);
    return new double[] {(double) dt / n, (double) (a1 - a0) / n};
  }

  public static void main(String[] args) throws Exception {
    int rounds = Integer.parseInt(System.getenv().getOrDefault("ROUNDS", "5"));
    long targetMs = Long.parseLong(System.getenv().getOrDefault("TARGET_MS", "700"));
    String[] models = {"small", "medium", "large"};
    java.util.Map<String, String> json = new java.util.LinkedHashMap<>();
    for (String m : models) json.put(m, Files.readString(Path.of("/fixtures/" + m + ".json")));

    ObjectMapper blackbird = ObjectSerializer.createDefaultObjectMapper().registerModule(new BlackbirdModule());
    ObjectMapper afterburner = ObjectSerializer.createDefaultObjectMapper().registerModule(new AfterburnerModule());
    java.util.Map<String, Codec> codecs = new java.util.LinkedHashMap<>();
    codecs.put("generated", generatedCodec());
    codecs.put("blackbird", mapperCodec(blackbird));
    codecs.put("afterburner", mapperCodec(afterburner));
    codecs.put("handstream", handCodec());

    /* One codec per JVM: sharing one JVM lets the JIT's type profiles from one codec slow the next. */
    String only = args.length > 0 ? args[0] : null;
    for (String m : models) {
      ObjectMapper tree = new ObjectMapper();
      String expected = normalise(tree, tree.readTree(json.get(m)));
      for (java.util.Map.Entry<String, Codec> e : codecs.entrySet()) {
        if (only != null && !only.equals(e.getKey())) continue;
        String out = normalise(tree, tree.readTree(e.getValue().encode(m, e.getValue().decode(m, json.get(m)))));
        if (!out.equals(expected)) System.err.println("MISMATCH " + e.getKey() + " " + m);
      }
    }

    for (int round = 0; round < rounds; round++) {
      for (java.util.Map.Entry<String, Codec> e : codecs.entrySet()) {
        if (only != null && !only.equals(e.getKey())) continue;
        for (String m : models) {
          Codec c = e.getValue();
          String text = json.get(m);
          double[] d = measure(() -> c.decode(m, text), targetMs);
          System.out.printf("java %s %s decode %.1f %.0f -%n", e.getKey(), m, d[0], d[1]);
          Object value = c.decode(m, text);
          double[] x = measure(() -> c.encode(m, value), targetMs);
          System.out.printf("java %s %s encode %.1f %.0f -%n", e.getKey(), m, x[0], x[1]);
        }
      }
    }
  }
}
