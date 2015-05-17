package org.jsc;

import org.jsc.ExpressionService.BindingResolver;

/**
 * Simple expression interface to get/set values
 * @author kzantow
 */
public interface Expr {
	/**
	 * Get the value of this expression in the given context
	 * @param ctx
	 * @return
	 */
	public Object getValue(BindingResolver ctx);
	
	/**
	 * Set the value of this expression in the given context; this may fail for read-only type expressions
	 * @param ctx
	 * @param val
	 */
	public void setValue(BindingResolver ctx, Object val);
}