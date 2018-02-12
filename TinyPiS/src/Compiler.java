import java.io.IOException;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

import CompilerBase.GlobalVariable;
import parser.TinyPiSLexer;
import parser.TinyPiSParser;

public class Compiler extends CompilerBase {
	void compileStmt(ASTNode ndx, Environment env) {
		if (ndx instanceof ASTCompoundStmtNode){
			ASTCompoundStmtNode nd = (ASTCompoundStmtNode) ndx;
			for (ASTNode stmt: nd.stmts) {
				compileStmt(stmt, env);
			}
		} else if (ndx instanceof ASTAssignStmtNode) {
			ASTAssignStmtNode nd = (ASTAssignStmtNode) ndx;
			Variable var = env.lookup(nd.var);
			if (var == null)
				throw new Error("undefined variable :"+nd.var);
			compileExpr(nd.expr, env);
			if (var instanceof GlobalVariable) {
				GlobalVariable globalVar = (GlobalVariable) var;
				emitLDC(REG_R1, globalVar.getLabel());
				emitSTR(REG_DST, REG_R1, 0);
			} else
				throw new Error("Not a global variable: "+nd.var);
		} else if (ndx instanceof ASTIfStmtNode) {
			ASTIfStmtNode nd = (ASTIfStmtNode) ndx;
			String elseLabel = freshLabel();
			String endLabel = freshLabel();
			compileExpr(nd.cond, env);
			emitRI("cmp", REG_DST, 0);
			emitJMP("beq", elseLabel);
			compileStmt(nd.thenClause, env);
			emitJMP("b", endLabel);
			emitLabel(elseLabel);
			compileStmt(nd.elseClause, env);
			emitLabel(endLabel);
		} else if (ndx instanceof ASTWhileStmtNode) {
			ASTWhileStmtNode nd = (ASTWhileStmtNode) ndx;
			String startLabel = freshLabel();
			String endLabel = freshLabel();
			emitLabel(startLabel);
			compileExpr(nd.cond, env);
			emitRI("cmp", REG_DST, 0);
			emitJMP("beq", endLabel);
			compileStmt(nd.stmt, env);
			emitJMP("b", startLabel);
			emitLabel(endLabel);
		} else if (ndx instanceof ASTPrintStmtNode){
			ASTPrintStmtNode  nd = (ASTPrintStmtNode) ndx;
			String loop1Label = freshLabel();
			String jmp1Label = freshLabel();
			String jmp2Label = freshLabel();
			String jmp3Label = freshLabel();
			
			emitPUSH(REG_R1);
			emitPUSH(REG_R2);
			emitPUSH(REG_R3);
			emitPUSH(REG_R4);
			emitPUSH(REG_R6);
			emitPUSH(REG_R7);
			
			compileExpr(nd.expr, env);
			emitRR("mov", REG_R1, REG_DST);
			emitRI("mov", REG_R2, 8);
			emitRI("mov",REG_R3,16);
			emitRR("ldr",REG_R4,"=buf+8");
			emitLabel(loop1Label);
			emitRRR("udiv",REG_R6,REG_R1,REG_R3);
			emitRRR("mul",REG_R7,REG_R6,REG_R3);
			emitRRR("sub",REG_R7,REG_R1,REG_R7);
			emitRI("cmp",REG_R7,10);
			emitJMP("bcs",jmp1Label);
			emitRI("add",REG_R7,'0');
			emitLabel(jmp2Label);
			emitSTRB(REG_R7,REG_R4,-1);
			emitRR("mov",REG_R1,REG_R6);
			emitRI("subs",REG_R2,1);
			emitJMP("bne",loop1Label);
			emitJMP("b",jmp3Label);
			emitLabel(jmp1Label);
			emitRI("add",REG_R7,'a');
			emitRI("sub",REG_R7,10);
			emitJMP("b",jmp2Label);
			emitLabel(jmp3Label);
			
			emitRI("mov",REG_R7,4);
			emitRI("mov",REG_DST,1);
			emitRR("ldr",REG_R1,"=buf");
			emitRI("mov",REG_R2,9);
			emitI("swi",0);
			
			emitPOP(REG_R1);
			emitPOP(REG_R2);
			emitPOP(REG_R3);
			emitPOP(REG_R4);
			emitPOP(REG_R6);
			emitPOP(REG_R7);
		}
		else
			throw new Error("Unknown expression: "+ndx);
	}

	void compileExpr(ASTNode ndx, Environment env) {
		if (ndx instanceof ASTBinaryExprNode) {
			ASTBinaryExprNode nd = (ASTBinaryExprNode) ndx;
			compileExpr(nd.lhs, env);
			emitPUSH(REG_R1);
			emitRR("mov", REG_R1, REG_DST);
			compileExpr(nd.rhs, env);
			if (nd.op.equals("+"))
				emitRRR("add", REG_DST, REG_R1, REG_DST);
			else if (nd.op.equals("-"))
				emitRRR("sub", REG_DST, REG_R1, REG_DST);
			else if (nd.op.equals("*"))
				emitRRR("mul", REG_DST, REG_R1, REG_DST);
			else if (nd.op.equals("/"))
				emitRRR("udiv", REG_DST, REG_R1, REG_DST);
			else if (nd.op.equals("&"))
				emitRRR("and", REG_DST, REG_R1, REG_DST);
			else if (nd.op.equals("|"))
				emitRRR("orr", REG_DST,REG_R1,REG_DST);
			else
				throw new Error("Unknwon operator: "+nd.op);
			emitPOP(REG_R1);
		} else if (ndx instanceof ASTNumberNode) {
			ASTNumberNode nd = (ASTNumberNode) ndx;
			emitLDC(REG_DST, nd.value);
		} else if (ndx instanceof ASTVarRefNode) {
			ASTVarRefNode nd = (ASTVarRefNode) ndx;
			Variable var = env.lookup(nd.varName);
			if (var == null)
				throw new Error("Undefined variable: "+nd.varName);
			if (var instanceof GlobalVariable) {
				GlobalVariable globalVar = (GlobalVariable) var;
				emitLDC(REG_DST, globalVar.getLabel());
				emitLDR(REG_DST, REG_DST, 0);
			} else
				throw new Error("Not a global variable: "+nd.varName);
		} else if (ndx instanceof ASTUnaryExprNode) {
			ASTUnaryExprNode nd = (ASTUnaryExprNode) ndx;
			compileExpr(nd.rhs, env);
			if (nd.op.equals("-")){
				emitRR("mvn",REG_DST,REG_DST);
				emitRRI("add",REG_DST,REG_DST,1);
			} else if (nd.op.equals("~")) {
				emitRR("mvn",REG_DST,REG_DST);
			}
		} else
			throw new Error("Unknown expression: "+ndx);
	}
	
	void compile(ASTNode ast) {
		Environment env = new Environment();
		ASTProgNode prog = (ASTProgNode) ast;
		System.out.println("\t.section .data");
		System.out.println("\t@ 大域変数の定義");
		for (String varName: prog.varDecls) {
			if (env.lookup(varName) != null)
				throw new Error("Variable redefined: "+varName);
			GlobalVariable v = addGlobalVariable(env, varName);
			emitLabel(v.getLabel());
			System.out.println("\t.word 0");
		}
		if (env.lookup("answer") == null) {
			GlobalVariable v = addGlobalVariable(env,"answer");
			emitLabel(v.getLabel());
			System.out.println("\t.word 0");
		}

		System.out.println("\t.section .text");
		System.out.println("\t.global _start");
		System.out.println("_start:");
		System.out.println("\t@ 式をコンパイルした命令列");
		compileStmt(prog.stmt, env);
		System.out.println("\t@ EXITシステムコール");
		GlobalVariable v = (GlobalVariable) env.lookup("answer");
		emitLDC(REG_DST, v.getLabel());
		emitLDR("r0", REG_DST, 0);
		emitRI("mov", "r7", 1);   // EXIT のシステムコール番号
		emitI("swi", 0);
		
		System.out.println(".section .data");
		emitLabel("buf");
		System.out.println("\t .space 8,0");
		System.out.println(".byte 0x0a");
	}

	public static void main(String[] args) throws IOException {
		ANTLRInputStream input = new ANTLRInputStream(System.in);
		TinyPiSLexer lexer = new TinyPiSLexer(input);
		CommonTokenStream token = new CommonTokenStream(lexer);
		TinyPiSParser parser = new TinyPiSParser(token);
		ParseTree tree = parser.prog();
		ASTGenerator astgen = new ASTGenerator();
		ASTNode ast = astgen.translate(tree);
		Compiler compiler = new Compiler();
		compiler.compile(ast);
	}
}