/* Copyright (C) 2009  Pierre-Marc Fournier
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301 USA
 */

#include "tp.h"
#include <ust/marker.h>
#include "usterr.h"

DEFINE_TRACE(hello_tptest);

void tptest_probe(int anint)
{
	DBG("in tracepoint probe...");
	trace_mark(ust, tptest, "anint %d", anint);
}

static void __attribute__((constructor)) init()
{
	DBG("connecting tracepoint...");
	register_trace_hello_tptest(tptest_probe);
}