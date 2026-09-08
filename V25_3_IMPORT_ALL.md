# ARK V25.3 — Import All

V25.3 adds one Import All action.

On first use, Android opens the official All Files Access settings page. After the user enables ARK and returns, the scan starts automatically.

Import All indexes:
- internal shared storage,
- exposed SD-card/shared-storage volumes,
- previously authorized non-local document-provider trees such as Drive.

It does not delete, move, rename, or duplicate the whole phone. Files are linked into Phone Intake and Unified Core. Other apps' private `/data/data/<package>` sandboxes still require exports from those apps.
