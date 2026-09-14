# Bundled fonts

Yoinker ships two typefaces, both under the SIL Open Font License 1.1, which
permits bundling them in an application. The full licence text for each is here.

| Family | Used for | Licence |
| --- | --- | --- |
| Inter | interface text — labels, buttons, metadata | [Inter-OFL.txt](Inter-OFL.txt) |
| Space Grotesk | screen titles and the wordmark | [SpaceGrotesk-OFL.txt](SpaceGrotesk-OFL.txt) |

Both were generated as static weights from the upstream variable fonts, because
variable-font axes need Android 8 and this app supports Android 7.

Text that comes from a website — video titles, channel names, file names — is
deliberately left in the system font instead. Neither family covers every script,
and a Japanese or Arabic title set in a Latin-only font renders as empty boxes.
