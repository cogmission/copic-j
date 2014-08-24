/* ---------------------------------------------------------------------
 * Numenta Platform for Intelligent Computing (NuPIC)
 * Copyright (C) 2014, Numenta, Inc.  Unless you have an agreement
 * with Numenta, Inc., for a separate license for this software code, the
 * following terms and conditions apply:
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as
 * published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses.
 *
 * http://numenta.org/licenses/
 * ---------------------------------------------------------------------
 */
package org.numenta.nupic.integration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.numenta.nupic.data.PatternMachine;
import org.numenta.nupic.data.SequenceMachine;
import org.numenta.nupic.model.Cell;
import org.numenta.nupic.model.DistalDendrite;
import org.numenta.nupic.model.Synapse;
import org.numenta.nupic.research.ComputeCycle;
import org.numenta.nupic.research.Connections;
import org.numenta.nupic.research.TemporalMemory;

import com.bethecoder.ascii_table.ASCIITable;

/**
 * Test utility to furnish test data.
 * 
 * @author Chetan Surpur
 * @author David Ray
 *
 */
public class TemporalMemoryTestMachine {
	private TemporalMemory temporalMemory;
	
	public TemporalMemoryTestMachine(TemporalMemory tm) {
		this.temporalMemory = tm;
	}
	
	public List<Set<Integer>> feedSequence(List<Set<Integer>> sequence, boolean learn) {
		List<Set<Integer>> results = new ArrayList<Set<Integer>>();
		List<Set<Cell>> interimResults = new ArrayList<Set<Cell>>();
		ComputeCycle result = null;
		for(Set<Integer> pattern : sequence) {
			if(pattern == SequenceMachine.NONE) {
				temporalMemory.reset();
			}else{
				int[] patt = toIntArray(pattern);
				result = temporalMemory.compute(patt, learn);
			}
			interimResults.add(result.predictiveCells());
		}
		for(Set<Cell> set : interimResults) {
			List<Integer> l = temporalMemory.asCellIndexes(set);
			results.add(new LinkedHashSet<Integer>(l));
		}
		return results;
	}
	
	public DetailedResults computeDetailedResults(List<Set<Integer>> results, List<Set<Integer>> sequence) {
		List<Integer> predictedActiveCellsList = new ArrayList<Integer>(Arrays.asList(new Integer[] { 0 }));
		List<Integer> predictedInactiveCellsList = new ArrayList<Integer>(Arrays.asList(new Integer[] { 0 }));
		List<Set<Integer>> predictedActiveColumnsList = new ArrayList<Set<Integer>>();
		predictedActiveColumnsList.add(new HashSet<Integer>());
		List<Set<Integer>> predictedInactiveColumnsList = new ArrayList<Set<Integer>>();
		predictedInactiveColumnsList.add(new HashSet<Integer>());
		List<Set<Integer>> unpredictedActiveColumnsList = new ArrayList<Set<Integer>>();
		unpredictedActiveColumnsList.add(new HashSet<Integer>());
		
		Iterator<Set<Integer>> seqIter = sequence.iterator();
		seqIter.next(); //Align the iterator to the iteration index below.
		for(int i = 1;i < results.size();i++) {
			Set<Integer> pattern = seqIter.next();
			
			int predictedActiveCells = 0;
			int predictedInactiveCells = 0;
			Set<Integer> predictedActiveColumns = new LinkedHashSet<Integer>();
			Set<Integer> predictedInactiveColumns = new LinkedHashSet<Integer>();
			Set<Integer> unpredictedActiveColumns = new LinkedHashSet<Integer>();
			
			if(pattern != SequenceMachine.NONE) {
				Set<Integer> prevPredictedCells = results.get(i-1);
				
				for(Integer prevPredictedCell : prevPredictedCells) {
					Integer prevPredictedColumn = prevPredictedCell / temporalMemory.getCellsPerColumn();
					
					if(pattern.contains(prevPredictedColumn)) {
						predictedActiveCells++;
						predictedActiveColumns.add(prevPredictedColumn);
					}else{
						predictedInactiveCells++;
			            predictedInactiveColumns.add(prevPredictedColumn);
					}
				}
				
				unpredictedActiveColumns.addAll(temporalMemory.subtract(
					new ArrayList<Integer>(predictedActiveColumns), new ArrayList<Integer>(pattern)));
			}
			
			predictedActiveCellsList.add(predictedActiveCells);
			predictedInactiveCellsList.add(predictedInactiveCells);
			predictedActiveColumnsList.add(predictedActiveColumns);
			predictedInactiveColumnsList.add(predictedInactiveColumns);
			unpredictedActiveColumnsList.add(unpredictedActiveColumns);
		}
		
		DetailedResults detailedResults = new DetailedResults();
		detailedResults.predictedActiveCellsList = predictedActiveCellsList;
		detailedResults.predictedInactiveCellsList = predictedInactiveCellsList;
		detailedResults.predictedActiveColumnsList = predictedActiveColumnsList;
		detailedResults.predictedInactiveColumnsList = predictedInactiveColumnsList;
		detailedResults.unpredictedActiveColumnsList = unpredictedActiveColumnsList;
		return detailedResults;
	}
	
	public String prettyPrintDetailedResults(DetailedResults detailedResults, List<Set<Integer>> inputSequence,
		PatternMachine patternMachine, int verbosity) {
		
		String [] header = { "Pattern",
	            "predicted active columns",
	            "predicted inactive columns",
	            "unpredicted active columns",
	            "# predicted active cells",
	            "# predicted inactive cells"};
		
		String[][] data = new String[inputSequence.size()][header.length];
		
		for(int i = 0;i < inputSequence.size();i++) {
			Set<Integer> pattern = inputSequence.get(i);
			
			String[] row = new String[header.length];
			if(pattern == SequenceMachine.NONE) {
				row = new String[] { "<reset>", "0", "0", "0", "0", "0" };
			}else{
				row[0] = patternMachine.prettyPrintPattern(pattern,  1);
				row[1] = patternMachine.prettyPrintPattern(detailedResults.predictedActiveColumnsList.get(i),  1);
				row[2] = patternMachine.prettyPrintPattern(detailedResults.predictedInactiveColumnsList.get(i),  1);
				row[3] = patternMachine.prettyPrintPattern(detailedResults.unpredictedActiveColumnsList.get(i),  1);
				row[4] = "" + detailedResults.predictedActiveCellsList.get(i);
				row[5] = "" + detailedResults.predictedInactiveCellsList.get(i);
			}
			data[i] = row;
		}
		
		String retVal = ASCIITable.getInstance().getTable(header, data, ASCIITable.ALIGN_CENTER);
		
		return retVal;
	}
	
	public String prettyPrintTemporalMemory() {
		Connections c = temporalMemory.getConnections();
		
		String text = "";
		
		text += "Column # / Cell #:\t\t{segment=[[Source Cell: column#, index, permanence], ...]}\n";
		text += "------------------------------------\n";
		
		int len = temporalMemory.getColumnDimensions()[0];
		for(int i = 0;i < len;i++) {
			List<Cell> cells = temporalMemory.getColumn(i).getCells();
			for(Cell cell : cells) {
				Map<DistalDendrite, List<String>> segmentDict = new LinkedHashMap<DistalDendrite, List<String>>();
				for(DistalDendrite seg : cell.getSegments(c)) {
					List<String> synapseList = new ArrayList<String>();
					for(Synapse synapse : seg.getAllSynapses(c)) {
						synapseList.add("[" + synapse.getSourceCell() + ", " + synapse.getPermanence() + "]");
					}
					segmentDict.put(seg, synapseList);
				}
				
				text += "Column " + i + " / " + "Cell " + cell.getIndex() + ":\t\t" + segmentDict + "\n";
			}
			text += "\n";
		}
		
		text += "------------------------------------\n";
		
		return text;
	}
	
	private int[] toIntArray(Set<Integer> pattern) {
		int[] retVal = new int[pattern.size()];
		int idx = 0;
		for(int i : pattern) {
			retVal[idx++] = i;
		}
		return retVal;
	}
	
	public static class DetailedResults {
		List<Integer> predictedActiveCellsList;
		List<Integer> predictedInactiveCellsList;
		List<Set<Integer>> predictedActiveColumnsList;
		List<Set<Integer>> predictedInactiveColumnsList;
		List<Set<Integer>> unpredictedActiveColumnsList;
	}
}
