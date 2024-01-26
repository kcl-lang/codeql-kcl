package com.kcl.parser;

import com.kcl.ast.Module;
import com.kcl.ast.*;

import java.util.List;

public class DefaultVisitor implements ContextVisitor<Void, String> {
    @Override
    public Void visit(Program node, String Id) {
        node.getPkgs().forEach((pkg, modules) -> modules.forEach(module -> this.visit(module, "pkg")));
        return null;
    }

    @Override
    public Void visit(Module node, String Id) {
        node.getBody().forEach(stmtNodeRef -> this.visit(stmtNodeRef, "module"));
        return null;
    }

    @Override
    public Void visit(List<? extends Node<?>> node, String Id) {
        node.forEach(node1 -> this.visit(node1, Id));
        return null;
    }

    @Override
    public Void visit(Node<?> node, String Id) {
        if (node == null) {
            return null;
        }

        Id = node.getId();

        switch (node.getNode()) {
            case Stmt stmt -> {
                switch (stmt) {
                    case SchemaStmt schemaStmt -> this.visit(schemaStmt, Id);
                    case AssignStmt assignStmt -> this.visit(assignStmt, Id);
                    case SchemaAttr schemaAttr -> this.visit(schemaAttr, Id);
                    default -> throw new RuntimeException(stmt.toString());
                }
            }

            case Expr expr -> {
                switch (expr) {
                    case SchemaExpr schemaExpr -> visit(schemaExpr, Id);
                    case ConfigExpr configExpr -> visit(configExpr, Id);
                    case NumberLit numberLit -> visit(numberLit, Id);
                    case IdentifierExpr identifierExpr -> visit(identifierExpr, Id);
                    default -> throw new RuntimeException(expr.toString());
                }
            }
            case Type type -> visit(type, Id);
            case ConfigEntry configEntry -> visit(configEntry, Id);
            case Identifier identifier -> visit(identifier, Id);
            case Keyword keyword -> visit(keyword, Id);
            case String string -> {

            }
            default -> throw new RuntimeException(node.toString());
        }
        return null;
    }


    @Override
    public Void visit(Comment node, String Id) {
        return null;
    }

    @Override
    public Void visit(SchemaStmt node, String Id) {
        if (node == null) return null;

        visit(node.getDoc(), Id);
        visit(node.getName(), Id);
        visit(node.getParentName(), Id);
        visit(node.getForHostName(), Id);
        visit(node.getArgs(), Id);
        visit(node.getMixins(), Id);
        visit(node.getBody(), Id);
        visit(node.getDecorators(), Id);
        visit(node.getChecks(), Id);
        visit(node.getIndexSignature(), Id);
        return null;
    }

    @Override
    public Void visit(SchemaAttr node, String Id) {
        visit(node.getName(), Id);
        visit(node.getOp(), Id);
        visit(node.getValue(), Id);
        return null;
    }

    @Override
    public Void visit(AssignStmt node, String Id) {
        visit(node.getTargets(), Id);
        visit(node.getValue(), Id);
        visit(node.getTy(), Id);
        return null;
    }


    @Override
    public Void visit(SchemaExpr node, String Id) {
        visit(node.getName(), Id);
        visit(node.getArgs(), Id);
        visit(node.getKwargs(), Id);
        visit(node.getConfig(), Id);
        return null;
    }

    @Override
    public Void visit(ConfigExpr node, String Id) {
        visit(node.getItems(), Id);
        return null;
    }

    @Override
    public Void visit(IdentifierExpr node, String Id) {
        visit(node.getNames(), Id);
        return null;
    }


    @Override
    public Void visit(NumberLit node, String Id) {
        return null;
    }

    @Override
    public Void visit(StringLit node, String Id) {
        return null;
    }


    @Override
    public Void visit(Type node, String Id) {
        return null;
    }

    @Override
    public Void visit(BasicType node, String Id) {
        return null;
    }


    @Override
    public Void visit(Arguments node, String Id) {
        return null;
    }

    @Override
    public Void visit(Keyword node, String Id) {
        visit(node.getArg(), Id);
        visit(node.getValue(), Id);
        return null;
    }

    public Void visit(ConfigEntry node, String Id) {
        visit(node.getKey(), Id);
        visit(node.getValue(), Id);
        return null;
    }

    @Override
    public Void visit(Identifier node, String Id) {
        return null;
    }

    @Override
    public Void visit(AugOp node, String Id) {
        return null;
    }

    @Override
    public Void visit(NumberLitValue node, String Id) {
        return null;
    }

    @Override
    public Void visit(NumberBinarySuffix node, String Id) {
        return null;
    }

    @Override
    public Void visit(ConfigEntryOperation node, String Id) {
        return null;
    }

    @Override
    public Void visit(ExprContext node, String Id) {
        return null;
    }
}
