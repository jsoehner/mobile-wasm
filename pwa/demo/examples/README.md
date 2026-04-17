# MobileWasm Wasm Examples

These examples implement the MobileWasm run ABI:

run(inPtr: i32, inLen: i32, outPtr: i32, outCap: i32) -> outLen: i32

They all export `memory` and `run`, so they can be loaded by this project.

## Files

- `fixed_ok.wat`: always returns `{"result":"ok"}`
- `fixed_error.wat`: always returns `{"error":"demo"}`
- `echo_wrapped.wat`: returns `{"echo":<input-json>}`

## Build

If you have WABT installed (`wat2wasm`):

```bash
wat2wasm fixed_ok.wat -o fixed_ok.wasm
wat2wasm fixed_error.wat -o fixed_error.wasm
wat2wasm echo_wrapped.wat -o echo_wrapped.wasm
```

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
