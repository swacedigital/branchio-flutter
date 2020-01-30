# branchio

A new flutter plugin project.

## Getting Started

This project is a starting point for a Flutter
[plug-in package](https://flutter.dev/developing-packages/),
a specialized package that includes platform-specific implementation code for
Android and/or iOS.

For help getting started with Flutter, view our
[online documentation](https://flutter.dev/docs), which offers tutorials,
samples, guidance on mobile development, and a full API reference.

## Android

Look at the example project's `AndroidManifest.xml` and modify your apps manifest with the values you find in the dashboard at https://dashboard.branch.io/account-settings/app.

## iOS

### Associated domains

The minimum links required are the main link and an alternate link for Branch to work.

`applinks:myappname.app.links`
`applinks:myapp-alternate.app.link`
`applinks:myapp-test.app.link`

Note that the `-alternate` part is important for the second associated domain, do not modify this part.
The `-test` is optional and is only used for testing.

### Adding plist entries

1. Add branch_app_domain with your live key domain
2. Add branch_key with your current Branch key
3. Add your URI scheme as URL Types -> Item 0 -> URL Schemes
