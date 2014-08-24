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
package org.numenta.nupic.research;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.numenta.nupic.model.Cell;
import org.numenta.nupic.model.Column;
import org.numenta.nupic.model.DistalDendrite;
import org.numenta.nupic.model.Synapse;

/**
 * Container for the many {@link Set}s maintained by the {@link TemporalMemory}
 * for computation results, and interim computation results.
 * 
 * @author David Ray
 */
public class ComputeCycle {
	Set<Cell> activeCells = new LinkedHashSet<Cell>();
	Set<Cell> winnerCells = new LinkedHashSet<Cell>();
	Set<Cell> predictiveCells = new LinkedHashSet<Cell>();
	Set<Column> predictedColumns = new LinkedHashSet<Column>();
	Set<DistalDendrite> activeSegments = new LinkedHashSet<DistalDendrite>();
	Set<DistalDendrite> learningSegments = new LinkedHashSet<DistalDendrite>();
	Map<DistalDendrite, Set<Synapse>> activeSynapsesForSegment = new LinkedHashMap<DistalDendrite, Set<Synapse>>();
	
	
	/**
	 * Constructs a new {@code ComputeCycle}
	 */
	public ComputeCycle() {}
	
	/**
	 * Constructs a new {@code ComputeCycle} initialized with
	 * the connections relevant to the current calling {@link Thread} for
	 * the specified {@link TemporalMemory}
	 * 
	 * @param	c		the current connections state of the TemporalMemory
	 */
	public ComputeCycle(Connections c) {
		this.activeCells = new LinkedHashSet<Cell>(c.activeCells());
		this.winnerCells = new LinkedHashSet<Cell>(c.winnerCells());
		this.predictiveCells = new LinkedHashSet<Cell>(c.predictiveCells());
		this.predictedColumns = new LinkedHashSet<Column>(c.predictedColumns());
		this.activeSegments = new LinkedHashSet<DistalDendrite>(c.activeSegments());
		this.learningSegments = new LinkedHashSet<DistalDendrite>(c.learningSegments());
		this.activeSynapsesForSegment = new LinkedHashMap<DistalDendrite, Set<Synapse>>(c.activeSynapsesForSegment());
	}
	
	/**
	 * Returns the current {@link Set} of active cells
	 * 
	 * @return	the current {@link Set} of active cells
	 */
	public Set<Cell> activeCells() {
		return activeCells;
	}
	
	/**
	 * Returns the current {@link Set} of winner cells
	 * 
	 * @return	the current {@link Set} of winner cells
	 */
	public Set<Cell> winnerCells() {
		return winnerCells;
	}
	
	/**
	 * Returns the {@link Set} of predictive cells.
	 * @return
	 */
	public Set<Cell> predictiveCells() {
		return predictiveCells;
	}
	
	/**
	 * Returns the current {@link Set} of predicted columns
	 * 
	 * @return	the current {@link Set} of predicted columns
	 */
	public Set<Column> predictedColumns() {
		return predictedColumns;
	}
	
	/**
	 * Returns the Set of learning {@link DistalDendrite}s
	 * @return
	 */
	public Set<DistalDendrite> learningSegments() {
		return learningSegments;
	}
	
	/**
	 * Returns the Set of active {@link DistalDendrite}s
	 * @return
	 */
	public Set<DistalDendrite> activeSegments() {
		return activeSegments;
	}
	
	/**
	 * Returns the mapping of Segments to active synapses in t-1
	 * @return
	 */
	public Map<DistalDendrite, Set<Synapse>> activeSynapsesForSegment() {
		return activeSynapsesForSegment;
	}
}
