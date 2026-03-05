#!/usr/bin/env bash
#
# Checks for theme violations in the OpenCode Android codebase.
#
# Detects:
#   1. Direct MaterialTheme.colorScheme usage outside approved files
#   2. Raw M3 components that should use Tui* wrappers
#   3. Hardcoded RoundedCornerShape usage outside Theme.kt
#
# Run:  ./scripts/check_theme_violations.sh
# Exit: 0 = clean, 1+ = number of violation categories found
#

set -euo pipefail

SRC="app/src/main/java"
ERRORS=0

echo "=== OpenCode Theme Violation Check ==="
echo ""

# 1. Direct MaterialTheme.colorScheme usage
echo "> Checking MaterialTheme.colorScheme usage..."
VIOLATIONS=$(grep -rn "MaterialTheme\.colorScheme" --include="*.kt" "$SRC" |
	grep -v "Material3Mapper.kt" |
	grep -v "Theme.kt" |
	grep -v "MainActivity.kt" |
	grep -v "Preview" |
	grep -v "ComponentPreviews" || true)

if [ -n "$VIOLATIONS" ]; then
	echo "  X Direct MaterialTheme.colorScheme usage found:"
	echo "$VIOLATIONS" | sed 's/^/    /'
	ERRORS=$((ERRORS + 1))
else
	echo "  OK No direct MaterialTheme.colorScheme usage"
fi

# 2. Raw AlertDialog usage (should use TuiAlertDialog)
echo "> Checking raw AlertDialog usage..."
VIOLATIONS=$(grep -rn "AlertDialog(" --include="*.kt" "$SRC" |
	grep -v "TuiComponents.kt" |
	grep -v "TuiAlertDialog" |
	grep -v "TuiConfirmDialog" |
	grep -v "import " || true)

if [ -n "$VIOLATIONS" ]; then
	echo "  WARN Raw AlertDialog( found (should use TuiAlertDialog):"
	echo "$VIOLATIONS" | sed 's/^/    /'
	ERRORS=$((ERRORS + 1))
else
	echo "  OK No raw AlertDialog usage"
fi

# 3. Raw Snackbar usage (should use themed Snackbar)
echo "> Checking raw Snackbar usage..."
VIOLATIONS=$(grep -rn "Snackbar(" --include="*.kt" "$SRC" |
	grep -v "TuiComponents.kt" |
	grep -v "ErrorBoundary.kt" |
	grep -v "TuiSnackbar" |
	grep -v "import " |
	grep -v "SnackbarHost" |
	grep -v "SnackbarHostState" |
	grep -v "SnackbarDuration" |
	grep -v "SnackbarResult" |
	grep -v "showSnackbar" || true)

if [ -n "$VIOLATIONS" ]; then
	echo "  WARN Raw Snackbar( found (consider using themed variant):"
	echo "$VIOLATIONS" | sed 's/^/    /'
	ERRORS=$((ERRORS + 1))
else
	echo "  OK No raw Snackbar usage"
fi

# 4. Raw Slider without explicit colors
echo "> Checking raw Slider usage..."
VIOLATIONS=$(grep -rn "^[[:space:]]*Slider(" --include="*.kt" "$SRC" |
	grep -v "TuiComponents.kt" |
	grep -v "import " || true)

# Filter out sliders that have 'colors =' within the next 10 lines
REAL_VIOLATIONS=""
if [ -n "$VIOLATIONS" ]; then
	while IFS= read -r line; do
		FILE=$(echo "$line" | cut -d: -f1)
		LINENO_VAL=$(echo "$line" | cut -d: -f2)
		# Check if 'colors' appears within the next 10 lines
		HAS_COLORS=$(sed -n "$((LINENO_VAL)),$((LINENO_VAL + 10))p" "$FILE" | grep -c "colors" || true)
		if [ "$HAS_COLORS" -eq 0 ]; then
			REAL_VIOLATIONS="$REAL_VIOLATIONS$line
"
		fi
	done <<<"$VIOLATIONS"
fi

if [ -n "$REAL_VIOLATIONS" ]; then
	echo "  WARN Slider( without explicit colors found:"
	echo "$REAL_VIOLATIONS" | sed 's/^/    /'
	ERRORS=$((ERRORS + 1))
else
	echo "  OK No raw Slider without explicit colors"
fi

# 5. Hardcoded RoundedCornerShape outside Theme.kt
echo "> Checking hardcoded RoundedCornerShape..."
VIOLATIONS=$(grep -rn "RoundedCornerShape" --include="*.kt" "$SRC" |
	grep -v "Theme.kt" |
	grep -v "ComponentPreviews" |
	grep -v "import " || true)

if [ -n "$VIOLATIONS" ]; then
	echo "  WARN RoundedCornerShape found outside Theme.kt:"
	echo "$VIOLATIONS" | sed 's/^/    /'
	ERRORS=$((ERRORS + 1))
else
	echo "  OK No hardcoded RoundedCornerShape"
fi

# 6. MaterialTheme.shapes usage outside Theme.kt
echo "> Checking MaterialTheme.shapes usage..."
VIOLATIONS=$(grep -rn "MaterialTheme\.shapes" --include="*.kt" "$SRC" |
	grep -v "Theme.kt" |
	grep -v "ComponentPreviews" || true)

if [ -n "$VIOLATIONS" ]; then
	echo "  WARN MaterialTheme.shapes found outside Theme.kt:"
	echo "$VIOLATIONS" | sed 's/^/    /'
	ERRORS=$((ERRORS + 1))
else
	echo "  OK No MaterialTheme.shapes usage"
fi

echo ""
if [ "$ERRORS" -gt 0 ]; then
	echo "=== $ERRORS violation category(s) found ==="
	exit "$ERRORS"
else
	echo "=== All checks passed ==="
	exit 0
fi
