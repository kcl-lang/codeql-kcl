package com.kcl.parser;

import com.kcl.ast.Module;
import com.kcl.ast.*;

import java.util.List;

public interface ContextVisitor<R, C> {
    public R visit(Program node, C c);

    public R visit(Module node, C c);

    public R visit(List<? extends Node<?>> nodes, C c);

    public R visit(Node<?> node, C c);

    public R visit(Comment node, C c);

    public R visit(SchemaStmt node, C c);

    public R visit(SchemaAttr node, C c);

    public R visit(AssignStmt node, C c);

    public R visit(SchemaExpr node, C c);

    public R visit(ConfigExpr node, C c);

    public R visit(IdentifierExpr node, C c);

    public R visit(NumberLit node, C c);

    public R visit(StringLit node, C c);

    public R visit(Type node, C c);

    public R visit(BasicType node, C c);

    public R visit(Arguments node, C c);

    public R visit(Keyword node, C c);

    public R visit(ConfigEntry node, C c);

    public R visit(Identifier node, C c);

    public R visit(AugOp node, C c);

    public R visit(NumberLitValue node, C c);

    public R visit(NumberBinarySuffix node, C c);

    public R visit(ConfigEntryOperation node, C c);

    public R visit(ExprContext node, C c);
}
