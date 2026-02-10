# Contributing

## Getting started

1. Fork the repo and clone it
2. Create a branch off `main` for your change
3. Make your changes
4. Run `./gradlew :app:compileDebugKotlin` to check for compile errors
5. Open a pull request against `main`

That's it. There is no complex setup beyond having Java 17 and the Android SDK.

## Code style

Follow what is already there. A few things to keep in mind:

- Use `LocalOpenCodeTheme.current` for colors, not `MaterialTheme.colorScheme`. The app has its own theme system and Material3 theming is not used for colors.
- Use `Spacing.*` and `Sizing.*` tokens for dimensions instead of hardcoded `.dp` values.
- Use `TuiShapes` for shapes. Everything has 0dp corners.
- Keep the terminal aesthetic. No rounded corners, no elevation shadows, no stock Material3 widgets unless there is no alternative.

## AI-assisted development

This project includes an `AGENTS.md` file with instructions for AI coding assistants. If you use Cursor, Claude Code, OpenCode, or similar tools, they can pick up project conventions from that file automatically.

## Reporting bugs

Open an issue. Include what you expected, what happened instead, and your device/Android version if relevant.

## License

By contributing, you agree that your contributions are licensed under the GPL v3.
