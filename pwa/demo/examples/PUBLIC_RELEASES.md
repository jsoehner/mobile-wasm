# Public Release Assets

GitHub Releases for this repository can publish example MobileWasm package ZIPs as downloadable assets.

Expected release assets:

- fixed_ok.zip
- fixed_error.zip
- echo_wrapped.zip
- fnv_report.zip
- math_double.zip
- math_sum.zip
- math_subtract.zip
- math_multiply.zip
- math_divide.zip
- SHA256SUMS.txt

Once a tagged release is published, the assets are available from the repository Releases page:

- https://github.com/jsoehner/mobile-wasm/releases

Each release asset ZIP is directly compatible with the Android app's "Install Package from URL" flow when paired with the SHA-256 value from `SHA256SUMS.txt`.
