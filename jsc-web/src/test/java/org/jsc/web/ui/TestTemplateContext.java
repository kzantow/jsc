package org.jsc.web.ui;

import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;

import org.jsc.Expr;
import org.jsc.ExpressionService;
import org.jsc.app.App;
import org.jsc.web.ui.TemplateProcessorTest.Page;
import org.junit.Test;

public class TestTemplateContext {

	@Test
	public void testComplexContext() throws Throwable {
		Page pg = new Page("parent");
		pg.pages = Arrays.asList(new Page("page 1"), new Page("page 2"));
		StringWriter w = new StringWriter();
		
		ExpressionService expr = new App().get(ExpressionService.class);
		
		Context ctx = new Context(new ObjectContext(pg));
		
		LoopComponent loop = new LoopComponent("p", expr.parseExpr("${pg}"));
		
		HashMap<String, Expr> attrs = new HashMap<String, Expr>();
		
		attrs.put("pg", expr.parseExpr("${pages}"));
		
		TemplateComponent tc = new TemplateComponent(null, attrs, loop);
		
		ElementOutputComponent eoc = new ElementOutputComponent("div", Collections.emptyMap());
		
		loop.getChildren().add(eoc);
		
		tc.render(ctx, w);
	}
}
