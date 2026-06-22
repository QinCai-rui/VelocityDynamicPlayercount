# VelocityDynamicPlayercount

Velocity proxy plugin that rewrites the server list ping to show a dynamic, configurable player count.

## Features

- **Dynamic player count display**: Shows `p/p+1` (e.g. `5/6`) on the server list ping
- **Version string override**: Customise the version line shown in the server list
- **Commands**: `/vdpc help`, `status`, `reload`, `update`

## Configuration

```yaml
mode: "current-plus-one"     # current-plus-one, fixed, or disabled
fixed-max-players: 100       # only used when mode is "fixed"
motd: ""                     # leave empty to keep default
version-override: ""         # leave empty to keep default
```

## Commands

| Command        | Description.       |
|----------------|--------------------|
| `/vdpc help`   | Show help          |
| `/vdpc status` | Show plugin status |
| `/vdpc reload` | Reload config      |
| `/vdpc update` | Check for updates  |

## Building

```bash
mvn -Drevision=1.0.0 package
```

## License

GNU General Public License v3.0
