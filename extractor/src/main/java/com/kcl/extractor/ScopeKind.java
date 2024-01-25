package com.kcl.extractor;

/**
 * A kind of scope, corresponding to the <code>@scope</code> type in the dbscheme.
 */
public enum ScopeKind {
    Package(0),
    Builtin(1),
    Schema(2),
    Loop(3),
    CondStmt(4),
    LambdaScope(5),
    ConfigScope(6);

    private int value;

    private ScopeKind(int value) {
        this.value = value;
    }

    /**
     * Returns the value identifying this scope kind in the database.
     */
    public int getValue() {
        return value;
    }
}
