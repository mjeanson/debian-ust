doc_examplesdir = ${docdir}/examples
doc_examples_easy_ustdir = ${docdir}/examples/easy-ust
doc_examples_gen_tpdir = ${docdir}/examples/gen-tp
doc_examples_demodir = ${docdir}/examples/demo
doc_examples_hello_static_libdir = ${docdir}/examples/hello-static-lib
doc_examples_demo_tracefdir = ${docdir}/examples/demo-tracef
doc_examples_demo_tracelogdir = ${docdir}/examples/demo-tracelog
doc_examples_clock_overridedir = ${docdir}/examples/clock-override
doc_examples_getcpu_overridedir = ${docdir}/examples/getcpu-override

if BUILD_JAVA_AGENT_WITH_JUL
doc_examples_java_juldir = ${docdir}/examples/java-jul
dist_doc_examples_java_jul_DATA = java-jul/Makefile \
				  java-jul/Hello.java \
				  java-jul/run \
				  java-jul/ApplicationContextExample.java \
				  java-jul/FilterChangeListenerExample.java \
				  java-jul/README
SUBDIRS_JUL = java-jul
endif

if BUILD_JAVA_AGENT_WITH_LOG4J
doc_examples_java_log4jdir = ${docdir}/examples/java-log4j
dist_doc_examples_java_log4j_DATA = java-log4j/Makefile \
				  java-log4j/Hello.java \
				  java-log4j/run
SUBDIRS_LOG4J = java-log4j
endif

if BUILD_PYTHON_AGENT
doc_examples_pythondir = ${docdir}/examples/python
dist_doc_examples_python_DATA = python/hello.py
endif

dist_doc_examples_DATA = README

dist_doc_examples_easy_ust_DATA = easy-ust/Makefile \
	easy-ust/sample.c \
	easy-ust/sample_component_provider.h easy-ust/tp.c

dist_doc_examples_gen_tp_DATA = gen-tp/Makefile \
	gen-tp/sample.c  gen-tp/sample_tracepoint.tp

dist_doc_examples_demo_DATA = demo/demo.c \
	demo/demo-trace \
	demo/Makefile \
	demo/README \
	demo/tp2.c \
	demo/tp3.c \
	demo/tp.c \
	demo/ust_tests_demo2.h \
	demo/ust_tests_demo3.h \
	demo/ust_tests_demo.h

dist_doc_examples_hello_static_lib_DATA = hello-static-lib/Makefile \
	hello-static-lib/hello.c \
	hello-static-lib/README \
	hello-static-lib/ust_tests_hello.h \
	hello-static-lib/tp.c

dist_doc_examples_demo_tracef_DATA = demo-tracef/Makefile \
	demo-tracef/demo-tracef.c \
	demo-tracef/README

dist_doc_examples_demo_tracelog_DATA = demo-tracelog/Makefile \
	demo-tracelog/demo-tracelog.c \
	demo-tracelog/README

dist_doc_examples_clock_override_DATA = clock-override/Makefile \
	clock-override/lttng-ust-clock-override-example.c \
	clock-override/run-clock-override \
	clock-override/README

dist_doc_examples_getcpu_override_DATA = getcpu-override/Makefile \
	getcpu-override/lttng-ust-getcpu-override-example.c \
	getcpu-override/run-getcpu-override \
	getcpu-override/README

if NO_SHARED
# Don't build examples if shared libraries support was explicitly
# disabled.
else
# Copies are for VPATH build support
SUBDIRS_PROXY = easy-ust demo hello-static-lib demo-tracef clock-override \
		getcpu-override demo-tracelog

if BUILD_GEN_TP_EXAMPLES
SUBDIRS_PROXY += gen-tp
endif

all-local:
	@if [ x"$(srcdir)" != x"$(builddir)" ]; then \
		for subdir in $(SUBDIRS_PROXY) $(SUBDIRS_JUL) $(SUBDIRS_LOG4J); do \
			cp -pfR $(srcdir)/$$subdir $(builddir); \
			chmod -R u+w $(builddir)/$$subdir; \
		done; \
	fi; \
	if [ x"$(shell echo "$(top_srcdir)" | grep "^/" | wc -l)" = x"1" ]; then \
		echo "Examples: absolute top_srcdir path $(top_srcdir)"; \
		rel_src_subdir=""; \
	else \
		echo "Examples: relative top_srcdir path $(top_srcdir)"; \
		rel_src_subdir="../"; \
	fi; \
	if [ x"$(shell echo "$(top_builddir)" | grep "^/" | wc -l)" = x"1" ]; then \
		echo "Examples: absolute top_builddir path $(top_builddir)"; \
		rel_build_subdir=""; \
	else \
		echo "Examples: relative top_builddir path $(top_builddir)"; \
		rel_build_subdir="../"; \
	fi; \
	for subdir in $(SUBDIRS_PROXY); do \
		(cd $$subdir && $(MAKE) AM_CC="$(CC)" AM_CPPFLAGS="$(CPPFLAGS) -I$$rel_src_subdir$(top_srcdir)/include/ -I$$rel_build_subdir$(top_builddir)/include/" AM_CFLAGS='$(CFLAGS)' AM_LDFLAGS='$(LDFLAGS) -L../../../liblttng-ust/.libs -Wl,-rpath="$(PWD)/../../liblttng-ust/.libs/" -Wl,-rpath-link="$(PWD)/../../liblttng-ust/.libs/"' LTTNG_GEN_TP_PATH="../../../tools/" $(AM_MAKEFLAGS) all && cd ..) || exit 1; \
	done; \
	if [ x"$(SUBDIRS_JUL)" != x"" ]; then \
		for subdir in $(SUBDIRS_JUL); do \
			(cd $(SUBDIRS_JUL) && $(MAKE) JAVA_CLASSPATH_OVERRIDE_JUL="../../../liblttng-ust-java-agent/java/lttng-ust-agent-jul" JAVA_CLASSPATH_OVERRIDE_COMMON="../../../liblttng-ust-java-agent/java/lttng-ust-agent-common" $(AM_MAKEFLAGS) all && cd ..) || exit 1; \
		done; \
	fi; \
	if [ x"$(SUBDIRS_LOG4J)" != x"" ]; then \
		for subdir in $(SUBDIRS_LOG4J); do \
			(cd $(SUBDIRS_LOG4J) && $(MAKE) JAVA_CLASSPATH_OVERRIDE_LOG4J="../../../liblttng-ust-java-agent/java/lttng-ust-agent-log4j" JAVA_CLASSPATH_OVERRIDE_COMMON="../../../liblttng-ust-java-agent/java/lttng-ust-agent-common" $(AM_MAKEFLAGS) all && cd ..) || exit 1; \
		done; \
	fi;

clean-local:
	@for subdir in $(SUBDIRS_PROXY); do \
		if [ -d $$subdir ]; then \
			(cd $$subdir && $(MAKE) $(AM_MAKEFLAGS) clean && cd ..) || exit 1; \
		fi; \
	done; \
	if [ x"$(SUBDIRS_JUL)" != x"" ]; then \
		for subdir in $(SUBDIRS_JUL); do \
			if [ -d $$subdir ]; then \
				(cd $$subdir && $(MAKE) $(AM_MAKEFLAGS) clean && cd ..) || exit 1; \
			fi; \
		done; \
	fi; \
	if [ x"$(SUBDIRS_LOG4J)" != x"" ]; then \
		for subdir in $(SUBDIRS_LOG4J); do \
			if [ -d $$subdir ]; then \
				(cd $$subdir && $(MAKE) $(AM_MAKEFLAGS) clean && cd ..) || exit 1; \
			fi; \
		done; \
	fi; \
	if [ x"$(srcdir)" != x"$(builddir)" ]; then \
		for subdir in $(SUBDIRS_PROXY) $(SUBDIRS_JUL) $(SUBDIRS_LOG4J); do \
			rm -rf $(builddir)/$$subdir; \
		done; \
	fi;
endif
