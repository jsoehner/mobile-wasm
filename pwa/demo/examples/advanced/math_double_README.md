# math_double.wat

Scans the input JSON for the first integer and returns:

{"value":"00042","double":"00084"}

Example input:

{"value":42}

Example output:

{"value":"00042","double":"00084"}

Notes:

- This is a lightweight parser for demo use, not a full JSON parser.
- It demonstrates actual input inspection, numeric parsing, arithmetic, and formatted JSON output.
- It implements the MobileWasm ABI and exports memory + run.
