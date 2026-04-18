# MobileWasm Wasm Examples

These examples implement the MobileWasm run ABI:

run(inPtr: i32, inLen: i32, outPtr: i32, outCap: i32) -> outLen: i32

They all export `memory` and `run`, so they can be loaded by this project.

## Files

- `fixed_ok.wat`: always returns `{"result":"ok"}`
- `fixed_error.wat`: always returns `{"error":"demo"}`
- `echo_wrapped.wat`: returns `{"echo":<input-json>}`

Advanced examples in `advanced/`:

- `math_double.wat`: reads first integer and returns zero-padded value + doubled value
- `math_sum.wat`: reads first two integers and returns `{"sum":<a+b>}`
- `math_subtract.wat`: reads first two integers and returns `{"difference":<a-b>}`
- `math_multiply.wat`: reads first two integers and returns `{"product":<a*b>}` with saturating overflow behavior
- `math_divide.wat`: reads first two integers and returns `{"quotient":<a/b>}` (truncate toward zero), or `{"error":"divide_by_zero"}`

## Build

If you have WABT installed (`wat2wasm`):

```bash
wat2wasm fixed_ok.wat -o fixed_ok.wasm
wat2wasm fixed_error.wat -o fixed_error.wasm
wat2wasm echo_wrapped.wat -o echo_wrapped.wasm
wat2wasm advanced/math_sum.wat -o math_sum.wasm
wat2wasm advanced/math_subtract.wat -o math_subtract.wasm
wat2wasm advanced/math_multiply.wat -o math_multiply.wasm
wat2wasm advanced/math_divide.wat -o math_divide.wasm
```

## Math input examples

Each math module scans for the first two integers in the input payload. JSON examples:

- `{"a": 7, "b": 5}`
- `{"left": -42, "right": 8}`

## Package for Android app (ZIP)

Example for `echo_wrapped.wasm`:

```bash
cat > manifest.json <<'JSON'
{
  "version": 1,
  "name": "echo-wrapped",
  "modules": [
    {
      "name": "echo_wrapped",
      "file": "echo_wrapped.wasm"
    }
  ]
}
JSON

zip echo-wrapped.zip manifest.json echo_wrapped.wasm
shasum -a 256 echo-wrapped.zip
```

Use that ZIP and SHA-256 in the app's "Install Package from URL" flow, or pick the ZIP file with "Install Package from ZIP File".
