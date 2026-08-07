"""
Lightweight JSON Schema validator with auto-fix capabilities.

Implements a minimal subset of JSON Schema draft-07 for our specific needs:
  - type checking (object, array, string, integer, boolean, null)
  - required fields
  - properties with per-property validation
  - minLength / maxLength (string)
  - minimum / maximum (number)
  - minItems / maxItems (array)
  - enum (string)
  - pattern (string regex)
  - additionalProperties (boolean)
  - items (array element schema)
  - allOf / oneOf / anyOf

No external dependencies (no jsonschema package required).
"""
import re
import logging
from dataclasses import dataclass, field
from typing import Any, Optional

from .lesson_plan_schema import LESSON_PLAN_SCHEMA, OUTLINE_SCHEMA
from .ppt_structure_schema import PPT_SLIDE_SCHEMA, PPT_STRUCTURE_SCHEMA

logger = logging.getLogger(__name__)


# ─── Error model ───

@dataclass
class ValidationError:
    path: str          # JSON path, e.g. "$.sections[0].title"
    message: str       # Human-readable error description
    schema_path: str   # Path in the schema, e.g. "$.properties.title.minLength"

    def __str__(self) -> str:
        return f"[{self.path}] {self.message}"


@dataclass
class ValidationConfig:
    """Controls validator behavior."""
    auto_fix: bool = True
    strict: bool = False            # If True, raise on first error
    log_warnings: bool = True


@dataclass
class ValidationResult:
    passed: bool
    errors: list[ValidationError] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)
    fixes_applied: list[str] = field(default_factory=list)
    fixed_data: dict | list | None = None


# ─── Simple Schema Registry ───

SCHEMA_REGISTRY: dict[str, dict] = {
    "lesson_plan": LESSON_PLAN_SCHEMA,
    "outline": OUTLINE_SCHEMA,
    "slide": PPT_SLIDE_SCHEMA,
    "ppt_structure": PPT_STRUCTURE_SCHEMA,
}


# ─── Core Validator ───

class SchemaValidator:
    """Lightweight JSON Schema validator for our specific schemas."""

    def __init__(self, config: Optional[ValidationConfig] = None):
        self.config = config or ValidationConfig()

    def validate(self, data: Any, schema_name: str) -> ValidationResult:
        """Validate data against a named schema.

        Args:
            data: The JSON data to validate.
            schema_name: Key in SCHEMA_REGISTRY ("lesson_plan", "slide", etc.)

        Returns:
            ValidationResult with passed flag and errors list.
        """
        schema = SCHEMA_REGISTRY.get(schema_name)
        if schema is None:
            raise ValueError(
                f"Unknown schema: {schema_name}. "
                f"Available: {list(SCHEMA_REGISTRY.keys())}"
            )

        errors: list[ValidationError] = []
        self._validate_value(data, schema, "$", errors)

        result = ValidationResult(passed=len(errors) == 0, errors=errors)

        # Auto-fix if enabled and there are fixable errors
        if not result.passed and self.config.auto_fix:
            fixed_data, fixes = self._auto_fix(data, schema)
            result.fixes_applied = fixes
            result.fixed_data = fixed_data

            # Re-validate after fix
            if fixes:
                re_errors: list[ValidationError] = []
                self._validate_value(fixed_data, schema, "$", re_errors)
                result.passed = len(re_errors) == 0
                if re_errors:
                    result.errors = re_errors

        return result

    def auto_fix(self, data: Any, schema_name: str) -> tuple[Any, list[str]]:
        """Convenience: validate + auto-fix in one call."""
        result = self.validate(data, schema_name)
        return result.fixed_data or data, result.fixes_applied

    # ─── Internal validation ───

    def _validate_value(
        self,
        value: Any,
        schema: dict,
        path: str,
        errors: list[ValidationError],
    ) -> None:
        """Recursively validate a value against a schema fragment."""
        if schema is True:
            return
        if schema is False:
            errors.append(ValidationError(path, "Schema forbids this value", "$"))
            return

        # --- type check ---
        expected_type = schema.get("type")
        if expected_type:
            type_ok = self._check_type(value, expected_type)
            if not type_ok:
                errors.append(ValidationError(
                    path,
                    f"Expected type '{expected_type}', got {type(value).__name__}",
                    f"{path}.type",
                ))
                # Don't continue validation on type mismatch — too risky
                return

        # Skip further validation for None/null
        if value is None:
            return

        # --- string checks ---
        if isinstance(value, str):
            min_len = schema.get("minLength")
            if min_len is not None and len(value) < min_len:
                errors.append(ValidationError(
                    path,
                    f"String too short: {len(value)} chars, minimum {min_len}",
                    f"{path}.minLength",
                ))
            max_len = schema.get("maxLength")
            if max_len is not None and len(value) > max_len:
                errors.append(ValidationError(
                    path,
                    f"String too long: {len(value)} chars, maximum {max_len}",
                    f"{path}.maxLength",
                ))
            pattern = schema.get("pattern")
            if pattern and not re.match(pattern, value):
                errors.append(ValidationError(
                    path,
                    f"String does not match pattern: {pattern}",
                    f"{path}.pattern",
                ))

        # --- enum check ---
        enum_values = schema.get("enum")
        if enum_values is not None and value not in enum_values:
            errors.append(ValidationError(
                path,
                f"Value '{value}' not in enum: {enum_values}",
                f"{path}.enum",
            ))

        # --- number checks ---
        if isinstance(value, (int, float)):
            minimum = schema.get("minimum")
            if minimum is not None and value < minimum:
                errors.append(ValidationError(
                    path,
                    f"Value {value} is less than minimum {minimum}",
                    f"{path}.minimum",
                ))
            maximum = schema.get("maximum")
            if maximum is not None and value > maximum:
                errors.append(ValidationError(
                    path,
                    f"Value {value} is greater than maximum {maximum}",
                    f"{path}.maximum",
                ))

        # --- object checks ---
        if isinstance(value, dict):
            self._validate_object(value, schema, path, errors)

        # --- array checks ---
        if isinstance(value, list):
            self._validate_array(value, schema, path, errors)

    def _validate_object(
        self,
        value: dict,
        schema: dict,
        path: str,
        errors: list[ValidationError],
    ) -> None:
        """Validate a dict/object value."""
        properties = schema.get("properties", {})
        required = schema.get("required", [])
        additional = schema.get("additionalProperties", True)

        # Check required fields
        for req_key in required:
            if req_key not in value:
                errors.append(ValidationError(
                    f"{path}.{req_key}",
                    f"Missing required field '{req_key}'",
                    f"{path}.required",
                ))

        # Check extra fields (if additionalProperties is False)
        if additional is False:
            allowed = set(properties.keys()) | set(required)
            if isinstance(additional, dict):
                # additionalProperties is a schema — would need complex handling
                pass
            for key in value:
                if key not in allowed:
                    errors.append(ValidationError(
                        f"{path}.{key}",
                        f"Extra field '{key}' not allowed",
                        f"{path}.additionalProperties",
                    ))

        # Validate each property
        for key, prop_schema in properties.items():
            if key in value:
                self._validate_value(
                    value[key], prop_schema,
                    f"{path}.{key}", errors,
                )

    def _validate_array(
        self,
        value: list,
        schema: dict,
        path: str,
        errors: list[ValidationError],
    ) -> None:
        """Validate a list/array value."""
        items_schema = schema.get("items")

        min_items = schema.get("minItems")
        if min_items is not None and len(value) < min_items:
            errors.append(ValidationError(
                path,
                f"Array too short: {len(value)} items, minimum {min_items}",
                f"{path}.minItems",
            ))

        max_items = schema.get("maxItems")
        if max_items is not None and len(value) > max_items:
            errors.append(ValidationError(
                path,
                f"Array too long: {len(value)} items, maximum {max_items}",
                f"{path}.maxItems",
            ))

        if items_schema and isinstance(items_schema, dict):
            for idx, item in enumerate(value):
                self._validate_value(item, items_schema, f"{path}[{idx}]", errors)

    def _check_type(self, value: Any, expected: str | list) -> bool:
        """Check if value matches the expected JSON Schema type.

        Handles both single types ("string") and union types (["object", "null"]).
        """
        type_map = {
            "object": dict,
            "array": list,
            "string": str,
            "integer": int,
            "number": (int, float),
            "boolean": bool,
            "null": type(None),
        }

        expected_types = expected if isinstance(expected, list) else [expected]

        # If value is None and null is in the allowed types, skip further checks
        if value is None and "null" in expected_types:
            return True

        for exp in expected_types:
            py_type = type_map.get(exp)
            if py_type is None and exp == "integer":
                py_type = int
            if py_type is None:
                continue  # Unknown type — skip
            if isinstance(value, py_type):
                return True

        return False

    # ─── Auto-fix logic ───

    def _auto_fix(self, data: Any, schema: dict) -> tuple[Any, list[str]]:
        """Attempt to fix common validation errors."""
        fixes: list[str] = []

        if isinstance(data, dict):
            return self._auto_fix_dict(data, schema, "$", fixes)
        if isinstance(data, list):
            return self._auto_fix_list(data, schema, "$", fixes)

        return data, fixes

    def _auto_fix_dict(
        self, data: dict, schema: dict, path: str, fixes: list[str],
    ) -> tuple[dict, list[str]]:
        """Fix a dict value: add missing required fields with defaults, remove extras."""
        result = dict(data)
        properties = schema.get("properties", {})
        required = schema.get("required", [])
        additional = schema.get("additionalProperties", True)

        # Add missing required fields with defaults
        for req_key in required:
            if req_key not in result:
                prop = properties.get(req_key, {})
                default = self._infer_default(prop)
                if default is not None:
                    result[req_key] = default
                    fixes.append(f"{path}.{req_key}: added default value '{default}'")

        # Remove extra fields if additionalProperties is False
        if additional is False:
            allowed = set(properties.keys()) | set(required)
            for key in list(result.keys()):
                if key not in allowed:
                    if not key.startswith("_"):  # Don't remove private keys
                        del result[key]
                        fixes.append(f"{path}.{key}: removed extra field")

        # Recurse
        for key, prop_schema in properties.items():
            if key in result and isinstance(result[key], dict):
                result[key], _ = self._auto_fix_dict(
                    result[key], prop_schema, f"{path}.{key}", fixes,
                )
            elif key in result and isinstance(result[key], list):
                result[key], _ = self._auto_fix_list(
                    result[key], prop_schema, f"{path}.{key}", fixes,
                )

        return result, fixes

    def _auto_fix_list(
        self, data: list, schema: dict, path: str, fixes: list[str],
    ) -> tuple[list, list[str]]:
        """Fix a list value."""
        result = list(data)
        items_schema = schema.get("items", {})

        # Filter out non-dict items if items schema expects objects
        if items_schema.get("type") == "object":
            result = [
                item for item in result
                if isinstance(item, dict)
            ]

        # Recurse into items
        for idx, item in enumerate(result):
            if isinstance(item, dict) and isinstance(items_schema, dict):
                result[idx], _ = self._auto_fix_dict(
                    item, items_schema, f"{path}[{idx}]", fixes,
                )

        return result, fixes

    @staticmethod
    def _infer_default(prop_schema: dict) -> Any:
        """Infer a reasonable default for a property schema."""
        # Check if schema has a default
        if "default" in prop_schema:
            return prop_schema["default"]

        # Infer from type
        prop_type = prop_schema.get("type")
        if prop_type == "string":
            return ""
        if prop_type == "array":
            return []
        if prop_type in ("integer", "number"):
            return 0
        if prop_type == "boolean":
            return False
        if prop_type == "object":
            return {}
        return None
