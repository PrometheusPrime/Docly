package android.print;

public final class DoclyPrintCallbacks {
    private DoclyPrintCallbacks() {
    }

    // Android SDK stubs expose these callback constructors as package-private.
    // Keeping this wrapper in android.print lets Kotlin use WebView's print adapter.
    public abstract static class Layout extends PrintDocumentAdapter.LayoutResultCallback {
        public Layout() {
            super();
        }
    }

    public abstract static class Write extends PrintDocumentAdapter.WriteResultCallback {
        public Write() {
            super();
        }
    }
}
