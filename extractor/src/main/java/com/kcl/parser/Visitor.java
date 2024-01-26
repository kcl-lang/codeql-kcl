package com.kcl.parser;

import com.kcl.ast.Module;
import com.kcl.ast.*;

import java.util.List;

public interface Visitor {
    public void visit(Program node);

    public void visit(Module node);

    public void visit(List<? extends Node<?>> nodes);

    public void visit(Node<?> node);

    public void visit(Comment node);

    public void visit(SchemaStmt node);

    public void visit(SchemaAttr node);

    public void visit(AssignStmt node);

    public void visit(SchemaExpr node);

    public void visit(ConfigExpr node);

    public void visit(IdentifierExpr node);

    public void visit(NumberLit node);

    public void visit(StringLit node);

    public void visit(Type node);

    public void visit(BasicType node);

    public void visit(Arguments node);

    public void visit(Keyword node);

    public void visit(ConfigEntry node);

    public void visit(Identifier node);

    public void visit(AugOp node);

    public void visit(NumberLitValue node);

    public void visit(NumberBinarySuffix node);

    public void visit(ConfigEntryOperation node);

    public void visit(ExprContext node);
}
