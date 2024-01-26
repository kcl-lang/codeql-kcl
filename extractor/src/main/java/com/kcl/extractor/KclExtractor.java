package com.kcl.extractor;

import com.kcl.api.Spec;
import com.kcl.ast.Module;
import com.kcl.ast.*;
import com.kcl.parser.KclAstParser;
import com.kcl.util.SematicUtil;
import com.semmle.util.collections.CollectionUtil;
import com.semmle.util.files.FileUtil;
import com.semmle.util.trap.TrapWriter;
import com.semmle.util.trap.TrapWriter.Label;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

public class KclExtractor implements IExtractor {

    private ExtractorConfig config;

    private TrapWriter trapWriter;

    private LocationManager locationManager;

    private TextualExtractor textualExtractor;

    private LexicalExtractor lexicalExtractor;

    private SyntacticContextManager contextManager;
    private ConcurrentMap<File, Optional<String>> packageTypeCache;

    private Spec.LoadPackage_Result specResult;

    private KclAstParser.ParseResult parseResult;

//    private Program

    public KclExtractor(ExtractorConfig config, ExtractorState state) {
        this.config = config;
        this.packageTypeCache = state.getPackageTypeCache();
        this.contextManager = new SyntacticContextManager();
    }

    @Override
    public ParseResultInfo extract(TextualExtractor textualExtractor) {
        this.textualExtractor = textualExtractor;
        this.locationManager = textualExtractor.getLocationManager();
        String sourceFile = textualExtractor.getExtractedFile().getAbsolutePath();
        String source = textualExtractor.getSource();
        ExtractionMetrics metrics = textualExtractor.getMetrics();
        this.trapWriter = textualExtractor.getTrapwriter();
        try {
            //parse file
            metrics.startPhase(ExtractionMetrics.ExtractionPhase.KclAstParser_parse);
            this.parseResult = KclAstParser.parse(Path.of(sourceFile));
            this.specResult = this.parseResult.getSpec();
            Path jsonPath = Path.of(System.getProperty("user.dir")).resolve("data/report/extend/kcl.json");
            FileUtil.write(jsonPath.toFile(), specResult.getProgram());
            metrics.stopPhase(ExtractionMetrics.ExtractionPhase.KclAstParser_parse);

            //extract
            metrics.startPhase(ExtractionMetrics.ExtractionPhase.KclExtractor_extract);
            this.lexicalExtractor = new LexicalExtractor(textualExtractor);
            Program program = this.parseResult.getProgram();
            visit(program);
            ParseResultInfo loc = lexicalExtractor.extractLines(source, locationManager.getFileLabel());
            metrics.stopPhase(ExtractionMetrics.ExtractionPhase.KclExtractor_extract);
            return loc;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void visit(Program program) {
        String root = program.getRoot();
        Label rootLabel = trapWriter.globalID("kcl;{" + root + "}," + this.locationManager.getStartLine() + ',' + this.locationManager.getStartColumn());
        this.trapWriter.addTuple("roots", rootLabel, root);

        program.getPkgs().forEach((pkg, modules) -> {
            Label packageLabel = this.trapWriter.freshLabel();
            this.trapWriter.addTuple("packages", packageLabel, pkg, rootLabel);

            AtomicInteger idx = new AtomicInteger();
            modules.forEach(module -> {
                Label moduleLabel = trapWriter.freshLabel();
                this.trapWriter.addTuple("modules", moduleLabel, module.getName(), locationManager.getFileLabel(), packageLabel, idx.get());

                contextManager.enterContainer(moduleLabel);
                this.visit(module, new Context(packageLabel, moduleLabel, idx.get()));
                contextManager.leaveContainer();
                idx.getAndIncrement();
//            emitNodeSymbol(nd, toplevelLabel);
            });
        });
    }

    public void visit(Module module, Context c) {
        visit(module.getBody(), c);
        int idx = 0;
        for (Node<Comment> commentNode : module.getComments()) {
            Label commentLabel = this.trapWriter.globalID(commentNode.getId());
            this.trapWriter.addTuple("comments", commentLabel, c.current, idx,
                    commentNode.getNode().getText(),
                    this.textualExtractor.mkToString(this.textualExtractor.getLine(commentNode))
            );
        }
    }

    public void visit(List<? extends Node<?>> listNode, Context c) {
        if (CollectionUtil.isNullOrEmpty(listNode)) {
            return;
        }

        Label listLabel = this.trapWriter.freshLabel();

        String tableName = switch (listNode.get(0).getNode()) {
            case String ignored -> "string_lists";
            case Identifier ignored -> "identifier_lists";
            case Expr ignored -> "expr_lists";
            case Stmt ignored -> "stmt_lists";
            case Keyword ignored -> "keyword_lists";
            default -> throw new RuntimeException(listNode.get(0).getId());
        };
        this.trapWriter.addTuple(tableName, listLabel, c.current, c.childIndex);
        int idx = 0;
        for (Node<?> node : listNode) {
            visit(node, new Context(c.current, listLabel, idx));
            idx++;
        }
    }


    public Label visit(Node<?> node, Context c) {
        if (node == null) {
            return null;
        }

        Label lbl = this.trapWriter.globalID(node.getId());
        c.pushLableInfo(lbl, node.getId());

        Context newContext = new Context(c.current, lbl, c.childIndex);
        newContext.pushLableInfo(lbl, node.getId());

        switch (node.getNode()) {
            case Stmt stmt -> {
                String tostring = this.textualExtractor.mkToString(this.textualExtractor.getLine(node));

                int kind = switch (stmt) {
                    case SchemaStmt ignored -> 9;
                    case AssignStmt ignored -> 3;
                    case SchemaAttr ignored -> 8;
                    default -> throw new RuntimeException(stmt.toString());
                };

                this.trapWriter.addTuple("stmts", lbl, kind, c.current, c.childIndex, tostring);

                contextManager.setCurrentStatement(stmt);

                switch (stmt) {
                    case SchemaStmt schemaStmt -> {
                        this.visit(schemaStmt, newContext);
                    }
                    case AssignStmt assignStmt -> {
                        this.visit(assignStmt, newContext);
                    }
                    case SchemaAttr schemaAttr -> this.visit(schemaAttr, newContext);
                    default -> throw new RuntimeException(stmt.toString());
                }
            }

            case Expr expr -> {
                int kind = switch (expr) {
                    case IdentifierExpr ignored -> 0;
                    case SchemaExpr ignored -> 14;
                    case ConfigExpr ignored -> 15;
                    case NumberLit ignored -> 22;
                    default -> throw new RuntimeException(expr.toString());
                };
                String tostring = this.textualExtractor.mkToString(this.textualExtractor.getLine(node));

                this.trapWriter.addTuple("exprs", lbl, kind, c.current, c.childIndex, tostring);

                switch (expr) {
                    case SchemaExpr schemaExpr -> visit(schemaExpr, newContext);
                    case ConfigExpr configExpr -> visit(configExpr, newContext);
                    case NumberLit numberLit -> visit(numberLit, newContext);
                    case IdentifierExpr identifierExpr -> {
                        Label identifierLable = visit(identifierExpr, newContext);
                        this.locationManager.emitNodeLocation(node, identifierLable);
                    }
                    default -> throw new RuntimeException(expr.toString());
                }
            }
            case String string -> {
                visit(string, newContext);
            }
            case Type type -> {
                visit(type, newContext);
            }
            case ConfigEntry configEntry -> {
                visit(configEntry, newContext);
            }
            case Identifier identifier -> {
                visit(identifier, newContext);
            }
            case Keyword keyword -> {
                visit(keyword, newContext);
            }
            default -> throw new RuntimeException(node.toString());
        }
        this.locationManager.emitNodeLocation(node, lbl);
        return lbl;
    }

    public void visit(SchemaStmt schemaStmt, Context c) {
        //string
        visit(schemaStmt.getDoc(), c.withNewIdx(0));
        visit(schemaStmt.getName(), c.withNewIdx(1));

        //identifier
        visit(schemaStmt.getParentName(), c.withNewIdx(0));
        visit(schemaStmt.getForHostName(), c.withNewIdx(1));

        //list<stmt>
        visit(schemaStmt.getBody(), c.withNewIdx(0));
    }

    public void visit(SchemaAttr schemaAttr, Context c) {
        //string
//        visit(schemaAttr.getDoc(), c.withNewIdx(0));
        visit(schemaAttr.getName(), c.withNewIdx(1));

        //augop
        visit(schemaAttr.getOp(), c.withNewIdx(0));

        //expr
        visit(schemaAttr.getValue(), c.withNewIdx(0));

        //list_expr
        visit(schemaAttr.getDecorators(), c.withNewIdx(0));

        //type
        visit(schemaAttr.getTy(), new Context(c.parent, c.current, 0));
    }

    public void visit(AssignStmt assignStmt, Context c) {
        //List<NodeRef<Identifier>>
        visit(assignStmt.getTargets(), c.withNewIdx(0));

        //expr
        visit(assignStmt.getValue(), c.withNewIdx(0));

        //type
        visit(assignStmt.getTy(), c.withNewIdx(0));
    }

    public void visit(SchemaExpr schemaExpr, Context c) {
        //identifier
        visit(schemaExpr.getName(), c.withNewIdx(0));

        //List<NodeRef<Expr>>
        visit(schemaExpr.getArgs(), c.withNewIdx(0));

        //List<NodeRef<Keyword>>
        visit(schemaExpr.getKwargs(), c.withNewIdx(0));

        //NodeRef<Expr>
        visit(schemaExpr.getConfig(), c.withNewIdx(0));
    }

    public Label visit(IdentifierExpr identifierExpr, Context c) {
        Label label = trapWriter.freshLabel();
        this.trapWriter.addTuple("identifiers", label, c.current, c.childIndex, identifierExpr.getName());

        //String
        this.visit(identifierExpr.getPkgpath(), new Context(label, trapWriter.freshLabel(), 0));

        //ExprContext
        this.visit(identifierExpr.getCtx(), new Context(label, trapWriter.freshLabel(), 0));
        return label;
    }

    public void visit(ConfigExpr configExpr, Context c) {
        int idx = 0;
        for (NodeRef<ConfigEntry> item : configExpr.getItems()) {
            this.visit(item, c.withNewIdx(idx));
            idx++;
        }
    }

    public void visit(NumberLit numberLit, Context c) {
        //NumberBinarySuffix
        if (numberLit.getBinarySuffix() != null && numberLit.getBinarySuffix().isPresent())
            visit(numberLit.getBinarySuffix().get(), c.withNewIdx(0));

        //NumberLitValue
        visit(numberLit.getValue(), c);
    }

    public void visit(StringLit stringLit, Context c) {
        this.trapWriter.addTuple("literals", trapWriter.freshLabel(), 3, c.current, stringLit.getValue());
    }


    public void visit(ConfigEntry configEntry, Context c) {
        this.trapWriter.addTuple("configentrys", c.current, c.parent, c.childIndex);

        //Expr
        visit(configEntry.getKey(), c.withNewIdx(0));
        visit(configEntry.getValue(), c.withNewIdx(1));

        //op
        visit(configEntry.getOperation(), c.withNewIdx(0));
    }

    public void visit(NumberBinarySuffix suffix, Context c) {
        this.trapWriter.addTuple("numberbinarysuffixs", trapWriter.freshLabel(), suffix.value(), c.parent);
    }

    public void visit(NumberLitValue numberLitValue, Context c) {
        String value;
        int kind;
        switch (numberLitValue) {
            case IntNumberLitValue litValue -> {
                value = String.valueOf(litValue.getValue());
                kind = 1;
            }
            case FloatNumberLitValue litValue -> {
                value = String.valueOf(litValue.getValue());
                kind = 2;
            }
            default -> throw new RuntimeException(numberLitValue.toString());
        }
//        Label label = ;
        this.trapWriter.addTuple("literals", trapWriter.freshLabel(), kind, c.current, value);
//        this.locationManager.emitNodeLocation(c.current, label);
    }

    public void visit(ConfigEntryOperation op, Context c) {
        Label opLabel = this.trapWriter.freshLabel();
        int kind = switch (op) {
            case Union -> 0;
            case Override -> 1;
            case Insert -> 2;
        };
        this.trapWriter.addTuple("configentry_operation", opLabel, kind, op.symbol(), c.current);
    }


    public Label visit(BasicType type, Context c) {
        int kind = switch (type.getValue()) {
            case Bool -> 2;
            case Int -> 3;
            case Float -> 4;
            case Str -> 5;
        };

        Label stringLable = trapWriter.freshLabel();
        this.visit(type.getValue().toString(), new Context(c.current, stringLable, 0));
        this.trapWriter.addTuple("types", c.current, kind, stringLable, c.parent, c.childIndex, type.getValue().toString());
        return c.current;
    }

    public void visit(Type type, Context c) {
        switch (type) {
            case BasicType basicType -> this.visit(basicType, c);
            default -> throw new RuntimeException(type.toString());
        }
    }

    public void visit(AugOp op, Context c) {
        if (op == null) {
            return;
        }
        Label augOp = trapWriter.freshLabel();
        int kind = switch (op) {
            case Assign -> 0;
            case Add -> 0;
            case Sub -> 1;
            case Mul -> 2;
            case Div -> 3;
            case Mod -> 4;
            case Pow -> 5;
            case FloorDiv -> 6;
            case LShift -> 7;
            case RShift -> 8;
            case BitXor -> 9;
            case BitAnd -> 10;
            case BitOr -> 11;
        };
        this.trapWriter.addTuple("augops", augOp, kind, c.parent, op.symbol());
    }


    public void visit(Identifier identifier, Context c) {
        this.trapWriter.addTuple("identifiers", c.current, c.parent, c.childIndex, identifier.getName());
        try {
            String astID = c.getNodeId(c.current);
            Spec.Symbol identSymbol = SematicUtil.findSymbolByAstId(this.specResult, astID);
            if (identSymbol != null && identSymbol.hasTy()) {
                String schemaFullName = identSymbol.getTy().getPkgPath() + "." + identSymbol.getTy().getSchemaName();
                Spec.Symbol appConfigSymbol = SematicUtil.findSymbol(specResult,
                        specResult.getFullyQualifiedNameMapOrDefault(schemaFullName, null));
                String nameId = SematicUtil.findNodeBySymbol(this.specResult, appConfigSymbol.getDef());
                String schemaId = this.parseResult.getSchemaMap().get(nameId);
                Node<?> schemaNode = this.parseResult.getNodeMap().get(schemaId);
                this.trapWriter.addTuple("schemas", c.current, this.trapWriter.globalID(schemaNode.getId()));
            }


        } catch (Exception e) {
            e.printStackTrace();
        }

        //String
        this.visit(identifier.getPkgpath(), new Context(c.current, trapWriter.freshLabel(), 1));

        //ExprContext
        this.visit(identifier.getCtx(), new Context(c.current, trapWriter.freshLabel(), 1));
    }

    public void visit(String node, Context c) {
        if (node == null) {
            return;
        }
        this.trapWriter.addTuple("strings", c.current, c.parent, c.childIndex, node);
    }

    public void visit(ExprContext exprContext, Context c) {
        int kind = switch (exprContext) {
            case Load -> 0;
            case Store -> 1;
        };
        this.trapWriter.addTuple("expr_contexts", c.current, kind, c.parent);
    }

    public void visit(Keyword keyword, Context c) {
        this.trapWriter.addTuple("keywords", c.current, c.childIndex);

        visit(keyword.getArg(), c.withNewIdx(0));
        visit(keyword.getValue(), c.withNewIdx(0));
    }

    private static class Context {
        private final TrapWriter.Label parent;
        private final TrapWriter.Label current;
        private final int childIndex;
        private final Map<Label, String> labelInfo = new HashMap<>();

        public Context(Label parent, int childIndex) {
            this.parent = parent;
            this.childIndex = childIndex;
            this.current = null;
        }

        public Context(Label parent, Label current, int childIndex) {
            this.parent = parent;
            this.current = current;
            this.childIndex = childIndex;
        }

        public Context withNewIdx(int childIndex) {
            return new Context(this.parent, this.current, childIndex);
        }

        public void pushLableInfo(Label label, String id) {
            this.labelInfo.put(label, id);
        }

        public String getNodeId(Label label) {
            return this.labelInfo.get(label);
        }

//        public Context(Label parent, int childIndex, IdContext idcontext, int kind) {
//            this(parent, childIndex, idcontext, kind, false);
//        }
//
//        public Context(Label parent, int childIndex, IdContext idcontext, int kind, boolean binopOperand) {
//            this.parent = parent;
//            this.childIndex = childIndex;
//            this.idcontext = idcontext;
//            this.binopOperand = binopOperand;
//        }

        /**
         * True if the visited AST node occurs as part of a type annotation.
         */
//        public boolean isInsideType() {
//            return idcontext.isInsideType();
//        }

        /**
         * True if the visited AST node occurs as one of the operands of a binary operation.
         */
//        public boolean isBinopOperand() {
//            return binopOperand;
//        }
    }

//    private void emitNodeSymbol(String def, Label key) {
//        int symbol = def.getSymbol();
//        if (symbol != -1) {
//            Label symbolLabel = trapwriter.globalID("symbol;" + def);
//            trapwriter.addTuple("ast_node_symbol", key, symbolLabel);
//        }
//    }

}


