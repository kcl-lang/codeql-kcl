package com.kcl.parser;

import com.kcl.api.API;
import com.kcl.api.Spec;
import com.kcl.ast.Node;
import com.kcl.ast.Program;
import com.kcl.ast.SchemaStmt;
import com.kcl.util.JsonUtil;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class KclAstParser {
    public static ParseResult parse(Path input) throws Exception {
        API api = new API();
        Map<String, Node<?>> nodeMap = new HashMap<>();
        Map<String, String> schemaMap = new HashMap<>();

        Spec.LoadPackage_Result specResult = api.loadPackage(
                Spec.LoadPackage_Args.newBuilder().setResolveAst(true).setWithAstIndex(true).setParseArgs(
                                Spec.ParseProgram_Args.newBuilder().addPaths(input.toString()).build())
                        .build());

        Program program = JsonUtil.deserializeProgram(specResult.getProgram());
        NodeVisitor nodeVisitor = new NodeVisitor(nodeMap, schemaMap);
        nodeVisitor.visit(program, "");
        return new ParseResult(specResult, nodeMap, program, schemaMap);
    }

    public static class ParseResult {
        private final Spec.LoadPackage_Result spec;
        private final Map<String, Node<?>> nodeMap;
        private final Map<String, String> schemaMap;
        private final Program program;

        public ParseResult(Spec.LoadPackage_Result spec, Map<String, Node<?>> nodeMap, Program program, Map<String, String> schemaMap) {
            this.spec = spec;
            this.nodeMap = nodeMap;
            this.program = program;
            this.schemaMap = schemaMap;
        }

        public Map<String, Node<?>> getNodeMap() {
            return nodeMap;
        }

        public Spec.LoadPackage_Result getSpec() {
            return spec;
        }

        public Program getProgram() {
            return program;
        }

        public Map<String, String> getSchemaMap() {
            return schemaMap;
        }
    }


    public static class NodeVisitor extends DefaultVisitor {
        private final Map<String, Node<?>> nodeMap;
        private final Map<String, String> schemaMap;

        public NodeVisitor(Map<String, Node<?>> nodeMap, Map<String, String> schemaMap) {
            this.nodeMap = nodeMap;
            this.schemaMap = schemaMap;
        }

        @Override
        public Void visit(Node<?> node, String Id) {
            if (node == null)
                return null;
            this.nodeMap.put(node.getId(), node);
            return super.visit(node, node.getId());
        }

        @Override
        public Void visit(SchemaStmt node, String Id) {
            this.schemaMap.put(node.getName().getId(), Id);
            super.visit(node, Id);
            return null;
        }
    }

}
