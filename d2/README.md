# Diagram sources (`d2/`)

This directory is the single source of truth for the project's diagrams. Each `.d2` file renders to
an SVG that is committed into the **wiki** repository under `images/` and referenced from wiki
pages as `![Description](images/<name>.svg)`.

## Layout

| Source | Rendered SVG (wiki) | Used by wiki page |
|--------|---------------------|-------------------|
| `architecture-modules.d2` | `images/architecture-modules.svg` | Architecture (Module Structure) |
| `dependency-flow.d2` | `images/dependency-flow.svg` | Architecture (Dependency Flow) |
| `type-hierarchy.d2` | `images/type-hierarchy.svg` | Architecture (Interface-First Design) |
| `registry-projection-flow.d2` | `images/registry-projection-flow.svg` | Reactive Events |
| `event-flow.d2` | `images/event-flow.svg` | Reactive Events |
| `player-state.d2` | `images/player-state.svg` | Audio Playback |
| `persistence-wiring.d2` | `images/persistence-wiring.svg` | SQL Persistence |
| `exception-hierarchy.d2` | `images/exception-hierarchy.svg` | Typed Exceptions |

## Rendering

Requires [d2](https://d2lang.com) on the `PATH` (`d2 --version`). Render a single diagram into the
wiki's `images/` directory (the wiki is a sibling checkout):

```bash
d2 d2/architecture-modules.d2 ../music-commons.wiki/images/architecture-modules.svg
```

Render all of them:

```bash
mkdir -p ../music-commons.wiki/images
for f in d2/*.d2; do
    d2 "$f" "../music-commons.wiki/images/$(basename "${f%.d2}").svg"
done
```

## Adding a diagram

1. Add a `<name>.d2` source file here.
2. Add a row to the table above.
3. Render it to `../music-commons.wiki/images/<name>.svg`.
4. Reference it from the relevant wiki page with `![Description](images/<name>.svg)`.
5. Commit the source here and the SVG in the wiki repo (they are separate git repositories).
