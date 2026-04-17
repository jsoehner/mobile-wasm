# Advanced MobileWasm Example

## fnv_report.wat

Computes a 32-bit FNV-1a digest over the input bytes and returns a JSON object:

{"inLen":"00042","fnv1a":"a1b2c3d4"}

Notes:

- Implements the required MobileWasm ABI: run(inPtr, inLen, outPtr, outCap) -> outLen
- Exports memory and run
- Uses 3 memory pages (compatible with the app's input/output offset expectations)
- Returns a fixed 36-byte JSON response

Build:

```bash
wat2wasm fnv_report.wat -o fnv_report.wasm
```

Package:

```bash
cat > manifest.json <<'JSON'
{
  "version": 1,
  "name": "fnv-report-package",
  "modules": [
    { "name": "fnv_report", "file": "fnv_report.wasm" }
  ]
}
JSON

zip fnv_report.zip manifest.json fnv_report.wasm
shasum -a 256 fnv_report.zip
```
