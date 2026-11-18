//! Runs against the generated rust SDK. Prints rows:
//! lang impl model op ns_per_op bytes_per_op allocs_per_op (one per round).
use petstore::models::{Pet, Tag};
use petstore::object_serializer;
use serde::de::DeserializeOwned;
use serde::Serialize;
use std::alloc::{GlobalAlloc, Layout, System};
use std::hint::black_box;
use std::sync::atomic::{AtomicU64, Ordering};
use std::time::Instant;

struct Counting;
static BYTES: AtomicU64 = AtomicU64::new(0);
static COUNT: AtomicU64 = AtomicU64::new(0);

unsafe impl GlobalAlloc for Counting {
    unsafe fn alloc(&self, layout: Layout) -> *mut u8 {
        BYTES.fetch_add(layout.size() as u64, Ordering::Relaxed);
        COUNT.fetch_add(1, Ordering::Relaxed);
        System.alloc(layout)
    }
    unsafe fn dealloc(&self, ptr: *mut u8, layout: Layout) {
        System.dealloc(ptr, layout)
    }
    unsafe fn realloc(&self, ptr: *mut u8, layout: Layout, new_size: usize) -> *mut u8 {
        BYTES.fetch_add(new_size as u64, Ordering::Relaxed);
        COUNT.fetch_add(1, Ordering::Relaxed);
        System.realloc(ptr, layout, new_size)
    }
}

#[global_allocator]
static A: Counting = Counting;

fn measure<F: FnMut()>(mut f: F, target_ms: u128) -> (f64, f64, f64) {
    for _ in 0..2000 {
        f();
    }
    let mut n: u64 = 1;
    loop {
        let t = Instant::now();
        for _ in 0..n {
            f();
        }
        let dt = t.elapsed().as_nanos();
        if dt > 50_000_000 {
            n = ((n as u128 * target_ms * 1_000_000 / dt) as u64).max(1);
            break;
        }
        n *= 4;
    }
    let b0 = BYTES.load(Ordering::Relaxed);
    let c0 = COUNT.load(Ordering::Relaxed);
    let t = Instant::now();
    for _ in 0..n {
        f();
    }
    let dt = t.elapsed().as_nanos() as f64;
    let b1 = BYTES.load(Ordering::Relaxed);
    let c1 = COUNT.load(Ordering::Relaxed);
    (dt / n as f64, (b1 - b0) as f64 / n as f64, (c1 - c0) as f64 / n as f64)
}

fn row(impl_: &str, model: &str, op: &str, r: (f64, f64, f64)) {
    println!("rust {impl_} {model} {op} {:.1} {:.0} {:.0}", r.0, r.1, r.2);
}

fn run<T: Serialize + DeserializeOwned + Clone>(model: &str, text: &str, rounds: usize, target_ms: u128) {
    let bytes = text.as_bytes();
    let value: T = serde_json::from_slice(bytes).unwrap();
    let again: T = object_serializer::deserialize::<T>(bytes).unwrap().unwrap();
    assert_eq!(
        serde_json::to_value(&value).unwrap(),
        serde_json::to_value(&again).unwrap()
    );
    for _ in 0..rounds {
        row("generated", model, "decode", measure(|| { black_box(object_serializer::deserialize::<T>(black_box(bytes)).unwrap()); }, target_ms));
        row("generated", model, "encode", measure(|| { black_box(object_serializer::serialize(black_box(&value)).unwrap()); }, target_ms));
        row("serdejson", model, "decode", measure(|| { black_box(serde_json::from_slice::<T>(black_box(bytes)).unwrap()); }, target_ms));
        row("serdejson", model, "encode", measure(|| { black_box(serde_json::to_string(black_box(&value)).unwrap()); }, target_ms));
        row("simdjson", model, "decode", measure(|| {
            let mut buf = bytes.to_vec();
            black_box(simd_json::serde::from_slice::<T>(&mut buf).unwrap());
        }, target_ms));
        row("simdjson", model, "encode", measure(|| { black_box(simd_json::serde::to_string(black_box(&value)).unwrap()); }, target_ms));
        row("sonic", model, "decode", measure(|| { black_box(sonic_rs::from_slice::<T>(black_box(bytes)).unwrap()); }, target_ms));
        row("sonic", model, "encode", measure(|| { black_box(sonic_rs::to_string(black_box(&value)).unwrap()); }, target_ms));
    }
}

fn main() {
    let rounds: usize = std::env::var("ROUNDS").ok().and_then(|v| v.parse().ok()).unwrap_or(5);
    let target_ms: u128 = std::env::var("TARGET_MS").ok().and_then(|v| v.parse().ok()).unwrap_or(700);
    let read = |n: &str| std::fs::read_to_string(format!("/fixtures/{n}.json")).unwrap();
    run::<Tag>("small", &read("small"), rounds, target_ms);
    run::<Pet>("medium", &read("medium"), rounds, target_ms);
    run::<Vec<Pet>>("large", &read("large"), rounds, target_ms);
}
