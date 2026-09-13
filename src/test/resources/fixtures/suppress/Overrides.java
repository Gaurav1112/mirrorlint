class Overrides {
    enum CliOption { MAX_WORKERS, HOOK_TIMEOUT, TEARDOWN_TIMEOUT, TAGS_FILTER }
    static final String[] PROJECT_OVERRIDES = {"MAX_WORKERS", "TAGS_FILTER", "FILE_PARALLELISM"};
    int use(Config config) {
        int a = config.MAX_WORKERS;
        int b = config.HOOK_TIMEOUT;
        return a + b;
    }
}
