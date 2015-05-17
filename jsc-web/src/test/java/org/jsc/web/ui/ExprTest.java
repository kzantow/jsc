package org.jsc.web.ui;

import java.util.HashMap;
import java.util.Map;

import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import org.jsc.Expr;
import org.jsc.ExpressionService;
import org.jsc.ExpressionService.BindingResolver;
import org.junit.Assert;
import org.junit.Test;

public class ExprTest {
	ExpressionService expr = new ExpressionService();
	@Test
	public void testExprs() {
		HashMap<String, Object> usr = new HashMap<String, Object>();
		usr.put("name", "joe");
		
		HashMap<String, Object> vars = new HashMap<String, Object>();
		vars.put("user", usr);
		BindingResolver ctx = new BindingResolver(vars);
		
		Expr exp = expr.parseExpr("${user.name}");
		org.junit.Assert.assertEquals("joe", exp.getValue(ctx));
		exp.setValue(ctx, "josh");
		Assert.assertEquals("josh", usr.get("name"));
		
		Expr exp2 = expr.parseExpr("${user.size()}");
		Assert.assertEquals(new Integer(1), exp2.getValue(ctx));
	}
	
	@Test
	public void testFunctions() {
		HashMap<String, Object> usr = new HashMap<String, Object>();
		usr.put("name", "joe");
		
		BindingResolver ctx = new BindingResolver(new ObjectContext(usr));
		
		Object o = expr.parseExpr("${size()}").getValue(ctx);
		Assert.assertEquals(new Integer(1), o);
	}
	
	@Test
	public void testGlobal() throws Exception {
		ScriptEngineManager manager = new ScriptEngineManager();
		ScriptEngine engine = manager.getEngineByName("nashorn");

		Map<String, Object> s = new HashMap<String, Object>();
		BindingResolver bindings = new BindingResolver(s);
		bindings.put("__val__", "ehhh");
		bindings.put("__ctx__", bindings);
		
		CompiledScript script = ((Compilable) engine).compile("__ctx__.mook = __val__"); // implicit return value
		
		script.eval(bindings);
		
		Assert.assertEquals("ehhh", bindings.get("mook"));
	}
}
