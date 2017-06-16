/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2017:
 * 	Una Thompson (unascribed),
 * 	Isaac Ellingson (Falkreon),
 * 	Jamie Mansfield (jamierocks),
 * 	and contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.elytradev.concrete.inventory.gui.widget;

import com.elytradev.concrete.inventory.gui.client.GuiDrawing;
import net.minecraft.inventory.IInventory;

public class WFieldedLabel extends WLabel {
	protected final IInventory inventory;
	protected final int field;
	protected final int fieldMax;

	public WFieldedLabel(IInventory inventory, int field, int fieldMax, String format, int color) {
		super(format, color);
		this.inventory = inventory;
		this.field = field;
		this.fieldMax = fieldMax;
	}

	public WFieldedLabel(IInventory inventory, int field, int fieldMax, String format) {
		this(inventory, field, fieldMax, format, DEFAULT_TEXT_COLOR);
	}

	public WFieldedLabel(IInventory inventory, int field, String format, int color) {
		this(inventory, field, -1, format, color);
	}

	public WFieldedLabel(IInventory inventory, int field, String format) {
		this(inventory, field, -1, format);
	}

	@Override
	public void paintBackground(int x, int y) {
		String formatted = text.replaceAll("%f", Integer.toString(inventory.getField(field)));
		if(fieldMax != -1) {
			formatted = formatted.replaceAll("%m", Integer.toString(inventory.getField(fieldMax)));
		}
		GuiDrawing.drawString(formatted, x, y, color);
	}
}
