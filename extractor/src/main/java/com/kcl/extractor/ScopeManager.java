//package com.kcl.extractor;
//
//import com.semmle.util.trap.TrapWriter;
//import com.semmle.util.trap.TrapWriter.Label;
//
//import java.util.*;
//
///**
// * Class for maintaining scoping information during extraction.
// */
//public class ScopeManager {
//    private static final Map<String, ScopeKind> scopeKinds = new LinkedHashMap<String, ScopeKind>();
//
//    static {
//        scopeKinds.put("Program", ScopeKind.GLOBAL);
//        scopeKinds.put("FunctionDeclaration", ScopeKind.FUNCTION);
//        scopeKinds.put("FunctionExpression", ScopeKind.FUNCTION);
//        scopeKinds.put("ArrowFunctionExpression", ScopeKind.FUNCTION);
//        scopeKinds.put("CatchClause", ScopeKind.CATCH);
//        scopeKinds.put("Module", ScopeKind.MODULE);
//        scopeKinds.put("BlockStatement", ScopeKind.BLOCK);
//        scopeKinds.put("SwitchStatement", ScopeKind.BLOCK);
//        scopeKinds.put("ForStatement", ScopeKind.FOR);
//        scopeKinds.put("ForInStatement", ScopeKind.FOR_IN);
//        scopeKinds.put("ForOfStatement", ScopeKind.FOR_IN);
//        scopeKinds.put("ComprehensionBlock", ScopeKind.COMPREHENSION_BLOCK);
//        scopeKinds.put("LetStatement", ScopeKind.BLOCK);
//        scopeKinds.put("LetExpression", ScopeKind.BLOCK);
//        scopeKinds.put("ClassExpression", ScopeKind.CLASS_EXPR);
//        scopeKinds.put("NamespaceDeclaration", ScopeKind.NAMESPACE);
//        scopeKinds.put("ClassDeclaration", ScopeKind.CLASS_DECL);
//        scopeKinds.put("InterfaceDeclaration", ScopeKind.INTERFACE);
//        scopeKinds.put("TypeAliasDeclaration", ScopeKind.TYPE_ALIAS);
//        scopeKinds.put("MappedTypeExpr", ScopeKind.MAPPED_TYPE);
//        scopeKinds.put("EnumDeclaration", ScopeKind.ENUM);
//        scopeKinds.put("ExternalModuleDeclaration", ScopeKind.EXTERNAL_MODULE);
//        scopeKinds.put("ConditionalTypeExpr", ScopeKind.CONDITIONAL_TYPE);
//    }
//
//    private final TrapWriter trapWriter;
//    private final Scope toplevelScope;
//    private final Set<String> implicitGlobals = new LinkedHashSet<>();
//    private final FileKind fileKind;
//    private Scope curScope;
//    private Scope implicitVariableScope;
//
//    public ScopeManager(TrapWriter trapWriter, FileKind fileKind) {
//        this.trapWriter = trapWriter;
//        this.toplevelScope = enterScope(ScopeKind.GLOBAL, trapWriter.globalID("global_scope"), null);
//        this.ecmaVersion = ecmaVersion;
//        this.implicitVariableScope = toplevelScope;
//        this.fileKind = fileKind;
//    }
//
//
//    public boolean isInTypeScriptDeclarationFile() {
//        return this.fileKind == FileKind.TYPESCRIPT_DECLARATION;
//    }
//
//    /**
//     * Sets the scope in which to declare variables that are referenced without
//     * being declared. This defaults to the global scope.
//     */
//    public void setImplicitVariableScope(Scope implicitVariableScope) {
//        this.implicitVariableScope = implicitVariableScope;
//    }
//
//    /**
//     * Reset the scope in which to declare variables that are referenced without
//     * being declared back to the global scope.
//     */
//    public void resetImplicitVariableScope() {
//        this.implicitVariableScope = toplevelScope;
//    }
//
//    /**
//     * Enter a new scope.
//     *
//     * @param scopeKind      the numeric scope kind
//     * @param scopeLabel     the label of the scope itself
//     * @param scopeNodeLabel the label of the AST node inducing this scope; may be null
//     */
//    public Scope enterScope(ScopeKind scopeKind, Label scopeLabel, Label scopeNodeLabel) {
//        Label outerScopeLabel = curScope == null ? null : curScope.scopeLabel;
//
//        curScope = new Scope(curScope, scopeLabel);
//
//        trapWriter.addTuple("scopes", curScope.scopeLabel, scopeKind.getValue());
//        if (scopeNodeLabel != null)
//            trapWriter.addTuple("scopenodes", scopeNodeLabel, curScope.scopeLabel);
//        if (outerScopeLabel != null)
//            trapWriter.addTuple("scopenesting", curScope.scopeLabel, outerScopeLabel);
//
//        return curScope;
//    }
//
//    /**
//     * Enter the scope induced by a given AST node.
//     */
//    public Scope enterScope(Node scopeNode) {
//        return enterScope(
//                scopeKinds.get(scopeNode.getType()),
//                trapWriter.freshLabel(),
//                trapWriter.localID(scopeNode));
//    }
//
//    /**
//     * Enters a scope for a block of form <code>declare global { ... }</code>.
//     *
//     * <p>Declarations in this block will contribute to the global scope, but references can still be
//     * resolved in the scope enclosing the declaration itself. The scope itself does not have its own
//     * label and will thus not exist at the QL level.
//     */
//    public void enterGlobalAugmentationScope() {
//        curScope = new Scope(curScope, toplevelScope.scopeLabel);
//    }
//
//    /**
//     * Re-enter a scope that was previously established.
//     */
//    public Scope reenterScope(Scope scope) {
//        return curScope = scope;
//    }
//
//    /**
//     * Leave the innermost scope.
//     */
//    public void leaveScope() {
//        curScope = curScope.outer;
//    }
//
//    public Scope getToplevelScope() {
//        return toplevelScope;
//    }
//
//    /**
//     * Get the label for a given variable in the current scope; if it cannot be found, add it to the
//     * implicit variable scope (usually the global scope).
//     */
//    public Label getVarKey(String name) {
//        Label lbl = curScope.lookupVariable(name);
//        if (lbl == null) {
//            lbl = addVariable(name, implicitVariableScope);
//            implicitGlobals.add(name);
//        }
//        return lbl;
//    }
//
//    /**
//     * Get the label for a given type in the current scope; or {@code null} if it cannot be found
//     * (unlike variables, there are no implicitly global type names).
//     */
//    public Label getTypeKey(String name) {
//        return curScope.lookupType(name);
//    }
//
//    /**
//     * Get the label for a given namespace in the current scope; or {@code null} if it cannot be found
//     * (unlike variables, there are no implicitly global namespace names).
//     */
//    public Label getNamespaceKey(String name) {
//        return curScope.lookupNamespace(name);
//    }
//
//    /**
//     * Add a variable to a given scope.
//     */
//    private Label addVariable(String name, Scope scope) {
//        Label key = trapWriter.globalID("var;{" + name + "};{" + scope.scopeLabel + "}");
//        scope.variableBindings.put(name, key);
//        trapWriter.addTuple("variables", key, name, scope.scopeLabel);
//        return key;
//    }
//
//    /**
//     * Add the given list of variables to the current scope.
//     */
//    public void addVariables(Iterable<String> names) {
//        for (String name : names) addVariable(name, curScope);
//    }
//
//    /**
//     * Convenience wrapper for {@link #addVariables(Iterable)}.
//     */
//    public void addVariables(String... names) {
//        addVariables(Arrays.asList(names));
//    }
//
//    /**
//     * Add a type name to a given scope.
//     */
//    private Label addTypeName(String name, Scope scope) {
//        Label key = trapWriter.globalID("local_type_name;{" + name + "};{" + scope.scopeLabel + "}");
//        scope.typeBindings.put(name, key);
//        trapWriter.addTuple("local_type_names", key, name, scope.scopeLabel);
//        return key;
//    }
//
//    /**
//     * Add a type name to the current scope.
//     */
//    public Label addTypeName(String name) {
//        return addTypeName(name, curScope);
//    }
//
//    /**
//     * Add the given list of type names to the current scope.
//     */
//    public void addTypeNames(Iterable<String> names) {
//        for (String name : names) addTypeName(name, curScope);
//    }
//
//    /**
//     * Adds a namespace name to the given scope.
//     */
//    private Label addNamespaceName(String name, Scope scope) {
//        Label key =
//                trapWriter.globalID("local_namespace_name;{" + name + "};{" + scope.scopeLabel + "}");
//        scope.namespaceBindings.put(name, key);
//        trapWriter.addTuple("local_namespace_names", key, name, scope.scopeLabel);
//        return key;
//    }
//
//    /**
//     * Add the given list of namespace names to the current scope.
//     */
//    public void addNamespaceNames(Iterable<String> names) {
//        for (String name : names) addNamespaceName(name, curScope);
//    }
//
//    /**
//     * Add the given list of variables to the current scope, returning the key for the last one.
//     */
//    public void addNames(DeclaredNames names) {
//        addVariables(names.getVariableNames());
//        addTypeNames(names.getTypeNames());
//        addNamespaceNames(names.getNamespaceNames());
//    }
//
//    /**
//     * Does the current scope declare the given variable?
//     */
//    public boolean declaredInCurrentScope(String name) {
//        return curScope.variableBindings.containsKey(name);
//    }
//
//    /**
//     * True if a declaration of 'name' is in scope, not counting implicit globals.
//     */
//    public boolean isStrictlyInScope(String name) {
//        for (Scope scope = curScope; scope != null; scope = scope.outer) {
//            if (scope.variableBindings.containsKey(name)
//                    && !(scope == toplevelScope && implicitGlobals.contains(name))) {
//                return true;
//            }
//        }
//        return false;
//    }
//
//    /**
//     * Collect all local variables, types, and namespaces declared in the given subtree. For
//     * convenience, 'subtree' is also allowed to be an array of subtrees, in which case all its
//     * elements are considered in turn.
//     *
//     * <p>{@code isStrict} indicates whether the subtree appears in strict-mode code (which influences
//     * the scoping of function declarations).
//     *
//     * <p>If 'blockscope' is true, only block-scoped declarations are considered, and traversal stops
//     * at block scope boundaries. Otherwise, only hoisted declarations are considered, and traversal
//     * stops at nested functions.
//     *
//     * <p>If 'declKind' is not {@link DeclKind#none}, then 'subtree' itself should be considered to be
//     * a declaration of the kind(s) specified.
//     */
//    public DeclaredNames collectDeclaredNames(
//            INode subtree, boolean isStrict, boolean blockscope, int declKind) {
//        return collectDeclaredNames(Collections.singletonList(subtree), isStrict, blockscope, declKind);
//    }
//
//    public DeclaredNames collectDeclaredNames(
//            List<? extends INode> subforest,
//            final boolean isStrict,
//            final boolean blockscope,
//            final int declKind) {
//        final Set<String> variableNames = new LinkedHashSet<String>();
//        final Set<String> typeNames = new LinkedHashSet<String>();
//        final Set<String> namespaceNames = new LinkedHashSet<String>();
//
//        new DefaultVisitor<Void, Void>() {
//            private int declKind;
//
//            private Void visit(INode nd) {
//                return visit(nd, DeclKind.none);
//            }
//
//            private Void visit(List<? extends INode> nds) {
//                return rec(nds, DeclKind.none);
//            }
//
//            private Void visit(INode nd, int declKind) {
//                if (nd != null) {
//                    int oldDeclKind = this.declKind;
//                    this.declKind = declKind;
//                    nd.accept(this, null);
//                    this.declKind = oldDeclKind;
//                }
//                return null;
//            }
//
//            private Void rec(List<? extends INode> nds, int declKind) {
//                if (nds != null) for (INode nd : nds) visit(nd, declKind);
//                return null;
//            }
//
//            // this is where we actually process declarations
//            @Override
//            public Void visit(Identifier nd, Void v) {
//                if (DeclKind.isVariable(declKind)) variableNames.add(nd.getName());
//                if (DeclKind.isType(declKind)) typeNames.add(nd.getName());
//                if (DeclKind.isNamespace(declKind)) namespaceNames.add(nd.getName());
//                return null;
//            }
//
//            // cases where we turn on the 'declKind' flags
//            @Override
//            public Void visit(FunctionDeclaration nd, Void v) {
//                if (nd.hasDeclareKeyword() && !isInTypeScriptDeclarationFile()) return null;
//                // strict mode functions are block-scoped, non-strict mode ones aren't
//                if (blockscope == isStrict) visit(nd.getId(), DeclKind.var);
//                return null;
//            }
//
//            @Override
//            public Void visit(ClassDeclaration nd, Void c) {
//                if (nd.hasDeclareKeyword() && !isInTypeScriptDeclarationFile()) return null;
//                if (blockscope) visit(nd.getClassDef().getId(), DeclKind.varAndType);
//                return null;
//            }
//
//            @Override
//            public Void visit(VariableDeclarator nd, Void v) {
//                return visit(nd.getId(), DeclKind.var);
//            }
//
//            @Override
//            public Void visit(InterfaceDeclaration nd, Void c) {
//                if (blockscope) visit(nd.getName(), DeclKind.type);
//                return null;
//            }
//
//            // cases where we stop traversal for block scoping
//            @Override
//            public Void visit(BlockStatement nd, Void v) {
//                if (!blockscope) visit(nd.getBody());
//                return null;
//            }
//
//            @Override
//            public Void visit(SwitchStatement nd, Void v) {
//                if (!blockscope) visit(nd.getCases());
//                return null;
//            }
//
//            @Override
//            public Void visit(ForStatement nd, Void v) {
//                if (!blockscope) {
//                    visit(nd.getInit());
//                    visit(nd.getBody());
//                }
//                return null;
//            }
//
//            @Override
//            public Void visit(EnhancedForStatement nd, Void v) {
//                if (!blockscope) {
//                    visit(nd.getLeft());
//                    visit(nd.getBody());
//                }
//                return null;
//            }
//
//            @Override
//            public Void visit(VariableDeclaration nd, Void v) {
//                if (nd.hasDeclareKeyword() && !isInTypeScriptDeclarationFile()) return null;
//                // in block scoping mode, only process 'let'; in non-block scoping
//                // mode, only process non-'let'
//                if (blockscope == nd.isBlockScoped(ecmaVersion)) visit(nd.getDeclarations());
//                return null;
//            }
//
//            @Override
//            public Void visit(LetExpression nd, Void v) {
//                if (blockscope) visit(nd.getHead());
//                return null;
//            }
//
//            @Override
//            public Void visit(LetStatement nd, Void v) {
//                if (blockscope) visit(nd.getHead());
//                return null;
//            }
//
//            @Override
//            public Void visit(Property nd, Void v) {
//                // properties only give rise to declarations if they are part of an object pattern
//                if (DeclKind.isVariable(declKind)) visit(nd.getValue(), DeclKind.var);
//                return null;
//            }
//
//            @Override
//            public Void visit(ClassBody nd, Void c) {
//                if (!blockscope) visit(nd.getBody());
//                return null;
//            }
//
//            @Override
//            public Void visit(NamespaceDeclaration nd, Void c) {
//                if (blockscope) return null;
//                boolean isAmbientOutsideDtsFile = nd.hasDeclareKeyword() && !isInTypeScriptDeclarationFile();
//                boolean hasVariable = nd.isInstantiated() && !isAmbientOutsideDtsFile;
//                visit(nd.getName(), hasVariable ? DeclKind.varAndNamespace : DeclKind.namespace);
//                return null;
//            }
//
//            // straightforward recursive cases
//            @Override
//            public Void visit(ArrayPattern nd, Void v) {
//                rec(nd.getElements(), declKind);
//                return visit(nd.getRest(), declKind);
//            }
//
//            @Override
//            public Void visit(ObjectPattern nd, Void v) {
//                rec(nd.getProperties(), declKind);
//                return visit(nd.getRest(), declKind);
//            }
//
//            @Override
//            public Void visit(IfStatement nd, Void v) {
//                visit(nd.getConsequent());
//                return visit(nd.getAlternate());
//            }
//
//            @Override
//            public Void visit(LabeledStatement nd, Void v) {
//                return visit(nd.getBody());
//            }
//
//            @Override
//            public Void visit(WithStatement nd, Void v) {
//                return visit(nd.getBody());
//            }
//
//            @Override
//            public Void visit(WhileStatement nd, Void v) {
//                return visit(nd.getBody());
//            }
//
//            @Override
//            public Void visit(DoWhileStatement nd, Void v) {
//                return visit(nd.getBody());
//            }
//
//            @Override
//            public Void visit(Program nd, Void v) {
//                return visit(nd.getBody());
//            }
//
//            @Override
//            public Void visit(TryStatement nd, Void v) {
//                visit(nd.getBlock());
//                visit(nd.getHandler());
//                visit(nd.getGuardedHandlers());
//                return visit(nd.getFinalizer());
//            }
//
//            @Override
//            public Void visit(CatchClause nd, Void v) {
//                return visit(nd.getBody());
//            }
//
//            @Override
//            public Void visit(SwitchCase nd, Void v) {
//                return visit(nd.getConsequent());
//            }
//
//            @Override
//            public Void visit(ExportDefaultDeclaration nd, Void c) {
//                return visit(nd.getDeclaration());
//            }
//
//            @Override
//            public Void visit(ExportNamedDeclaration nd, Void c) {
//                return visit(nd.getDeclaration());
//            }
//
//            @Override
//            public Void visit(ImportDeclaration nd, Void c) {
//                return visit(nd.getSpecifiers());
//            }
//
//            @Override
//            public Void visit(ImportSpecifier nd, Void c) {
//                return visit(nd.getLocal(), DeclKind.all);
//            }
//
//            @Override
//            public Void visit(ImportWholeDeclaration nd, Void c) {
//                return visit(nd.getLhs(), DeclKind.all);
//            }
//
//            @Override
//            public Void visit(EnumDeclaration nd, Void c) {
//                if (!blockscope) return null;
//                // Enums always occupy a place in all three declaration spaces.
//                // In some contexts it is a compilation error to reference the enum name
//                // (e.g. a const enum used as namespace), but we do not need to model this
//                // in the scope analysis.
//                return visit(nd.getId(), DeclKind.all);
//            }
//
//            @Override
//            public Void visit(TypeAliasDeclaration nd, Void c) {
//                if (!blockscope) return null;
//                return visit(nd.getId(), DeclKind.type);
//            }
//        }.rec(subforest, declKind);
//
//        return new DeclaredNames(variableNames, typeNames, namespaceNames);
//    }
//
//    public Set<String> collectDeclaredInferTypes(ITypeExpression expr) {
//        final Set<String> result = new LinkedHashSet<>();
//        expr.accept(
//                new DefaultVisitor<Void, Void>() {
//                    @Override
//                    public Void visit(InferTypeExpr nd, Void c) {
//                        // collect all the names declared by `infer R` types.
//                        result.add(nd.getTypeParameter().getId().getName());
//                        return null;
//                    }
//
//                    @Override
//                    public Void visit(ConditionalTypeExpr nd, Void c) {
//                        // Note: inhibit traversal into condition, since variables declared in the
//                        // condition are bound to the inner conditional expression.
//                        nd.getTrueType().accept(this, c);
//                        nd.getFalseType().accept(this, c);
//                        return null;
//                    }
//
//                    @Override
//                    public Void visit(ArrayTypeExpr nd, Void c) {
//                        return nd.getElementType().accept(this, c);
//                    }
//
//                    @Override
//                    public Void visit(FunctionTypeExpr nd, Void c) {
//                        return nd.getFunction().accept(this, c);
//                    }
//
//                    @Override
//                    public Void visit(FunctionExpression nd, Void c) {
//                        if (nd.getReturnType() != null) {
//                            nd.getReturnType().accept(this, c);
//                        }
//                        for (ITypeExpression paramType : nd.getParameterTypes()) {
//                            if (paramType != null) {
//                                paramType.accept(this, c);
//                            }
//                        }
//                        // note: `infer` types may not occur in type parameter bounds.
//                        return null;
//                    }
//
//                    @Override
//                    public Void visit(GenericTypeExpr nd, Void c) {
//                        for (ITypeExpression typeArg : nd.getTypeArguments()) {
//                            typeArg.accept(this, c);
//                        }
//                        return null;
//                    }
//
//                    @Override
//                    public Void visit(IndexedAccessTypeExpr nd, Void c) {
//                        nd.getObjectType().accept(this, null);
//                        nd.getIndexType().accept(this, null);
//                        return null;
//                    }
//
//                    @Override
//                    public Void visit(InterfaceTypeExpr nd, Void c) {
//                        for (MemberDefinition<?> member : nd.getBody()) {
//                            member.accept(this, null);
//                        }
//                        return null;
//                    }
//
//                    @Override
//                    public Void visit(IntersectionTypeExpr nd, Void c) {
//                        for (ITypeExpression type : nd.getElementTypes()) {
//                            type.accept(this, null);
//                        }
//                        return null;
//                    }
//
//                    @Override
//                    public Void visit(PredicateTypeExpr nd, Void c) {
//                        return nd.getTypeExpr().accept(this, c);
//                    }
//
//                    @Override
//                    public Void visit(ParenthesizedTypeExpr nd, Void c) {
//                        return nd.getElementType().accept(this, c);
//                    }
//
//                    @Override
//                    public Void visit(TupleTypeExpr nd, Void c) {
//                        for (ITypeExpression type : nd.getElementTypes()) {
//                            type.accept(this, null);
//                        }
//                        return null;
//                    }
//
//                    @Override
//                    public Void visit(UnionTypeExpr nd, Void c) {
//                        for (ITypeExpression type : nd.getElementTypes()) {
//                            type.accept(this, null);
//                        }
//                        return null;
//                    }
//                },
//                null);
//        return result;
//    }
//
//    public static enum FileKind {
//        /**
//         * Any file not specific to one of the other file kinds.
//         */
//        PLAIN,
//
//
//        /**
//         * A d.ts file, containing TypeScript ambient declarations.
//         */
//        TYPESCRIPT_DECLARATION,
//    }
//
//    public static class Scope {
//        private final Scope outer;
//        private final Label scopeLabel;
//        private final HashMap<String, Label> variableBindings = new LinkedHashMap<>();
//        private final HashMap<String, Label> typeBindings = new LinkedHashMap<>();
//        private final HashMap<String, Label> namespaceBindings = new LinkedHashMap<>();
//
//        public Scope(Scope outer, Label scopeLabel) {
//            this.outer = outer;
//            this.scopeLabel = scopeLabel;
//        }
//
//        public Label lookupVariable(String name) {
//            Label l = variableBindings.get(name);
//            if (l == null && outer != null) return outer.lookupVariable(name);
//            return l;
//        }
//
//        public Label lookupType(String name) {
//            Label l = typeBindings.get(name);
//            if (l == null && outer != null) return outer.lookupType(name);
//            return l;
//        }
//
//        public Label lookupNamespace(String name) {
//            Label l = namespaceBindings.get(name);
//            if (l == null && outer != null) return outer.lookupNamespace(name);
//            return l;
//        }
//    }
//
//    public static class DeclKind {
//        public static final int var = 1 << 0;
//        public static final int type = 1 << 1;
//        public static final int namespace = 1 << 2;
//
//        public static final int none = 0;
//        public static final int varAndType = var | type;
//        public static final int varAndNamespace = var | namespace;
//        public static final int all = var | type | namespace;
//
//        public static boolean isVariable(int kind) {
//            return (kind & var) != 0;
//        }
//
//        public static boolean isType(int kind) {
//            return (kind & type) != 0;
//        }
//
//        public static boolean isNamespace(int kind) {
//            return (kind & namespace) != 0;
//        }
//    }
//}
