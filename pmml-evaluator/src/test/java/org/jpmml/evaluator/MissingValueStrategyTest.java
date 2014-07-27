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

import java.util.Map;

import org.dmg.pmml.FieldName;
import org.dmg.pmml.MissingValueStrategyType;
import org.dmg.pmml.TreeModel;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MissingValueStrategyTest extends ModelEvaluatorTest {

	@Test
	public void nullPrediction() throws Exception {
		Map<FieldName, ?> arguments = createArguments("outlook", "sunny", "temperature", null, "humidity", null);

		NodeClassificationMap result = evaluate(MissingValueStrategyType.NULL_PREDICTION, arguments);

		assertEquals(null, getEntityId(result));
	}

	@Test
	public void lastPrediction() throws Exception {
		Map<FieldName, ?> arguments = createArguments("outlook", "sunny", "temperature", null, "humidity", null);

		NodeClassificationMap result = evaluate(MissingValueStrategyType.LAST_PREDICTION, arguments);

		assertEquals("2", getEntityId(result));

		assertEquals((Double)0.8d, result.getProbability("will play"));
		assertEquals((Double)0.04d, result.getProbability("may play"));
		assertEquals((Double)0.16d, result.getProbability("no play"));
	}

	@Test
	public void defaultChildSingle() throws Exception {
		Map<FieldName, ?> arguments = createArguments("outlook", null, "temperature", 40d, "humidity", 70d);

		NodeClassificationMap result = evaluate(MissingValueStrategyType.DEFAULT_CHILD, 0.8d, arguments);

		assertEquals("4", getEntityId(result));

		assertEquals((Double)0.4d, result.getProbability("will play"));
		assertEquals((Double)0d, result.getProbability("may play"));
		assertEquals((Double)0.6d, result.getProbability("no play"));

		double missingValuePenatly = 0.8d;

		assertEquals((Double)(0.4d * missingValuePenatly), result.getConfidence("will play"));
		assertEquals((Double)(0d * missingValuePenatly), result.getConfidence("may play"));
		assertEquals((Double)(0.6d * missingValuePenatly), result.getConfidence("no play"));
	}

	@Test
	public void defaultChildMultiple() throws Exception {
		Map<FieldName, ?> arguments = createArguments("outlook", null, "temperature", null, "humidity", 70d);

		NodeClassificationMap result = evaluate(MissingValueStrategyType.DEFAULT_CHILD, 0.8d, arguments);

		assertEquals("3", getEntityId(result));

		assertEquals((Double)0.9d, result.getProbability("will play"));
		assertEquals((Double)0.05d, result.getProbability("may play"));
		assertEquals((Double)0.05d, result.getProbability("no play"));

		double missingValuePenalty = (0.8d * 0.8d);

		assertEquals((Double)(0.9d * missingValuePenalty), result.getConfidence("will play"));
		assertEquals((Double)(0.05d * missingValuePenalty), result.getConfidence("may play"));
		assertEquals((Double)(0.05d * missingValuePenalty), result.getConfidence("no play"));
	}

	private NodeClassificationMap evaluate(MissingValueStrategyType missingValueStrategy, Map<FieldName, ?> arguments) throws Exception {
		return evaluate(missingValueStrategy, null, arguments);
	}

	private NodeClassificationMap evaluate(MissingValueStrategyType missingValueStrategy, Double missingValuePenalty, Map<FieldName, ?> arguments) throws Exception {
		ModelEvaluator<?> evaluator = createModelEvaluator();

		TreeModel treeModel = ((TreeModel)evaluator.getModel())
			.withMissingValueStrategy(missingValueStrategy)
			.withMissingValuePenalty(missingValuePenalty);

		Map<FieldName, ?> result = evaluator.evaluate(arguments);

		return (NodeClassificationMap)result.get(evaluator.getTargetField());
	}
}