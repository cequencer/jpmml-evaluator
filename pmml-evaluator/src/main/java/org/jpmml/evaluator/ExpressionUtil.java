/*
 * Copyright (c) 2013 Villu Ruusmann
 *
 * This file is part of JPMML-Evaluator
 *
 * JPMML-Evaluator is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JPMML-Evaluator is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with JPMML-Evaluator.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.jpmml.evaluator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.dmg.pmml.Aggregate;
import org.dmg.pmml.Apply;
import org.dmg.pmml.Constant;
import org.dmg.pmml.DataField;
import org.dmg.pmml.DataType;
import org.dmg.pmml.DerivedField;
import org.dmg.pmml.Discretize;
import org.dmg.pmml.Expression;
import org.dmg.pmml.FieldColumnPair;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.FieldRef;
import org.dmg.pmml.InvalidValueTreatmentMethodType;
import org.dmg.pmml.MapValues;
import org.dmg.pmml.NormContinuous;
import org.dmg.pmml.NormDiscrete;

public class ExpressionUtil {

	private ExpressionUtil(){
	}

	static
	public FieldValue evaluate(FieldName name, EvaluationContext context){
		Map.Entry<FieldName, FieldValue> entry = context.getFieldEntry(name);
		if(entry != null){
			return entry.getValue();
		}

		EvaluationContext.Result<DerivedField> result = context.resolveDerivedField(name);
		if(result != null){
			FieldValue value = evaluate(result.getElement(), context);

			context.declare(name, value);

			EvaluationContext resultContext = result.getContext();
			if(!(resultContext).equals(context)){
				resultContext.declare(name, value);
			}

			return value;
		}

		return null;
	}

	static
	public FieldValue evaluate(DerivedField derivedField, EvaluationContext context){
		FieldValue value = evaluate(derivedField.getExpression(), context);

		return FieldValueUtil.refine(derivedField, value);
	}

	static
	public FieldValue evaluate(Expression expression, EvaluationContext context){

		if(expression instanceof Constant){
			return evaluateConstant((Constant)expression, context);
		} else

		if(expression instanceof FieldRef){
			return evaluateFieldRef((FieldRef)expression, context);
		} else

		if(expression instanceof NormContinuous){
			return evaluateNormContinuous((NormContinuous)expression, context);
		} else

		if(expression instanceof NormDiscrete){
			return evaluateNormDiscrete((NormDiscrete)expression, context);
		} else

		if(expression instanceof Discretize){
			return evaluateDiscretize((Discretize)expression, context);
		} else

		if(expression instanceof MapValues){
			return evaluateMapValues((MapValues)expression, context);
		} else

		if(expression instanceof Apply){
			return evaluateApply((Apply)expression, context);
		} else

		if(expression instanceof Aggregate){
			return evaluateAggregate((Aggregate)expression, context);
		}

		throw new UnsupportedFeatureException(expression);
	}

	/**
	 * @throws TypeAnalysisException If the data type cannot be determined.
	 */
	static
	public DataType getDataType(Expression expression, ModelEvaluator<?> modelEvaluator){

		if(expression instanceof Constant){
			Constant constant = (Constant)expression;

			String value = constant.getValue();

			DataType dataType = constant.getDataType();
			if(dataType == null){
				dataType = TypeUtil.getConstantDataType(value);
			}

			return dataType;
		} else

		if(expression instanceof FieldRef){
			FieldRef fieldRef = (FieldRef)expression;

			FieldName name = fieldRef.getField();

			DataField dataField = modelEvaluator.getDataField(name);
			if(dataField != null){
				return dataField.getDataType();
			}

			DerivedField derivedField = modelEvaluator.resolveDerivedField(name);
			if(derivedField != null){
				return derivedField.getDataType();
			}

			throw new TypeAnalysisException(expression);
		} else

		if(expression instanceof NormContinuous){
			return DataType.DOUBLE;
		} else

		if(expression instanceof NormDiscrete){
			return DataType.DOUBLE;
		} else

		if(expression instanceof Discretize){
			Discretize discretize = (Discretize)expression;

			DataType dataType = discretize.getDataType();
			if(dataType == null){
				dataType = DataType.STRING;
			}

			return dataType;
		} else

		if(expression instanceof MapValues){
			MapValues mapValues = (MapValues)expression;

			DataType dataType = mapValues.getDataType();
			if(dataType == null){
				dataType = DataType.STRING;
			}

			return dataType;
		} else

		if(expression instanceof Apply){
			throw new TypeAnalysisException(expression);
		} else

		if(expression instanceof Aggregate){
			throw new TypeAnalysisException(expression);
		}

		throw new UnsupportedFeatureException(expression);
	}

	static
	public FieldValue evaluateConstant(Constant constant, EvaluationContext context){
		String value = constant.getValue();

		DataType dataType = constant.getDataType();
		if(dataType == null){
			dataType = TypeUtil.getConstantDataType(value);
		}

		return FieldValueUtil.create(dataType, null, value);
	}

	static
	public FieldValue evaluateFieldRef(FieldRef fieldRef, EvaluationContext context){
		FieldValue value = evaluate(fieldRef.getField(), context);
		if(value == null){
			return FieldValueUtil.create(fieldRef.getMapMissingTo());
		}

		return value;
	}

	static
	public FieldValue evaluateNormContinuous(NormContinuous normContinuous, EvaluationContext context){
		FieldValue value = evaluate(normContinuous.getField(), context);
		if(value == null){
			return FieldValueUtil.create(normContinuous.getMapMissingTo());
		}

		return NormalizationUtil.normalize(normContinuous, value);
	}

	static
	public FieldValue evaluateNormDiscrete(NormDiscrete normDiscrete, EvaluationContext context){
		FieldValue value = evaluate(normDiscrete.getField(), context);
		if(value == null){
			return FieldValueUtil.create(normDiscrete.getMapMissingTo());
		}

		NormDiscrete.Method method = normDiscrete.getMethod();
		switch(method){
			case INDICATOR:
				{
					boolean equals = value.equalsString(normDiscrete.getValue());

					return FieldValueUtil.create(equals ? 1d : 0d);
				}
			default:
				throw new UnsupportedFeatureException(normDiscrete, method);
		}
	}

	static
	public FieldValue evaluateDiscretize(Discretize discretize, EvaluationContext context){
		FieldValue value = evaluate(discretize.getField(), context);
		if(value == null){
			return FieldValueUtil.create(discretize.getDataType(), null, discretize.getMapMissingTo());
		}

		return DiscretizationUtil.discretize(discretize, value);
	}

	static
	public FieldValue evaluateMapValues(MapValues mapValues, EvaluationContext context){
		Map<String, FieldValue> values = new LinkedHashMap<>();

		List<FieldColumnPair> fieldColumnPairs = mapValues.getFieldColumnPairs();
		for(FieldColumnPair fieldColumnPair : fieldColumnPairs){
			FieldValue value = evaluate(fieldColumnPair.getField(), context);
			if(value == null){
				return FieldValueUtil.create(mapValues.getDataType(), null, mapValues.getMapMissingTo());
			}

			values.put(fieldColumnPair.getColumn(), value);
		}

		return DiscretizationUtil.mapValue(mapValues, values);
	}

	static
	public FieldValue evaluateApply(Apply apply, EvaluationContext context){
		String mapMissingTo = apply.getMapMissingTo();

		List<Expression> expressions = apply.getExpressions();

		Iterator<Expression> arguments = expressions.iterator();

		List<FieldValue> values = new ArrayList<>();

		String name = apply.getFunction();

		condition:
		if(("if").equals(name)){

			if(arguments.hasNext()){
				FieldValue flag = evaluate(arguments.next(), context);

				if(flag == null && mapMissingTo != null){
					return FieldValueUtil.create(mapMissingTo);
				}

				values.add(flag);

				// Skip both THEN and ELSE parts
				if(flag == null){

					if(arguments.hasNext()){
						arguments.next();

						values.add(null);

						if(arguments.hasNext()){
							arguments.next();

							values.add(null);
						}
					}

					break condition;
				} // End if

				// Evaluate THEN part, skip ELSE part
				if(flag.asBoolean()){

					if(arguments.hasNext()){
						FieldValue trueValue = evaluate(arguments.next(), context);

						if(trueValue == null && mapMissingTo != null){
							return FieldValueUtil.create(mapMissingTo);
						}

						values.add(trueValue);

						if(arguments.hasNext()){
							arguments.next();

							values.add(null);
						}
					}
				} else

				// Skip THEN part, evaluate ELSE part
				{
					if(arguments.hasNext()){
						arguments.next();

						values.add(null);

						if(arguments.hasNext()){
							FieldValue falseValue = evaluate(arguments.next(), context);

							if(falseValue == null && mapMissingTo != null){
								return FieldValueUtil.create(mapMissingTo);
							}

							values.add(falseValue);
						}
					}
				}
			}
		}

		while(arguments.hasNext()){
			FieldValue value = evaluate(arguments.next(), context);

			// "If a mapMissingTo value is specified and any of the input values of the function are missing, then the function is not applied at all and the mapMissingTo value is returned instead"
			if(value == null && mapMissingTo != null){
				return FieldValueUtil.create(mapMissingTo);
			}

			values.add(value);
		}

		String defaultValue = apply.getDefaultValue();

		FieldValue result;

		try {
			result = FunctionUtil.evaluate(apply, values, context);
		} catch(InvalidResultException ire){
			InvalidValueTreatmentMethodType invalidValueTreatmentMethod = apply.getInvalidValueTreatment();

			switch(invalidValueTreatmentMethod){
				case RETURN_INVALID:
					throw new InvalidResultException(apply);
				case AS_IS:
					// Re-throw the given InvalidResultException instance
					throw ire;
				case AS_MISSING:
					return FieldValueUtil.create(defaultValue);
				default:
					throw new UnsupportedFeatureException(apply, invalidValueTreatmentMethod);
			}
		}

		// "If a defaultValue value is specified and the function produced a missing value, then the defaultValue is returned"
		if(result == null && defaultValue != null){
			return FieldValueUtil.create(defaultValue);
		}

		return result;
	}

	@SuppressWarnings (
		value = {"rawtypes", "unchecked"}
	)
	static
	public FieldValue evaluateAggregate(Aggregate aggregate, EvaluationContext context){
		FieldValue value = evaluate(aggregate.getField(), context);

		Collection<?> values;

		// The JPMML library operates with single records, so it's impossible to implement "proper" aggregation over multiple records
		// It is assumed that the aggregation has been performed by application developer beforehand
		try {
			values = (Collection<?>)FieldValueUtil.getValue(value);
		} catch(ClassCastException cce){
			throw new TypeCheckException(Collection.class, value);
		}

		FieldName groupName = aggregate.getGroupField();
		if(groupName != null){
			FieldValue groupValue = evaluate(groupName, context);

			// Ensure that the group value is a simple type, not a collection type
			TypeUtil.getDataType(FieldValueUtil.getValue(groupValue));
		}

		// Remove missing values
		values = Lists.newArrayList(Iterables.filter(values, Predicates.notNull()));

		Aggregate.Function function = aggregate.getFunction();
		switch(function){
			case COUNT:
				return FieldValueUtil.create(values.size());
			case SUM:
				return Functions.SUM.evaluate(FieldValueUtil.createAll((List<?>)values));
			case AVERAGE:
				return Functions.AVG.evaluate(FieldValueUtil.createAll((List<?>)values));
			case MIN:
				return FieldValueUtil.create(Collections.min((List<Comparable>)values));
			case MAX:
				return FieldValueUtil.create(Collections.max((List<Comparable>)values));
			default:
				throw new UnsupportedFeatureException(aggregate, function);
		}
	}
}