export PROJDIR   := $(shell pwd)
export TARGETDIR := $(PROJDIR)/bin/build
export PGSQLDIR  := $(PROJDIR)/../pgsql
export SETTINGS  := $(shell set)

.PHONY: all

all: pljava

%:
		@mkdir -p $(TARGETDIR)/pljava
		@$(MAKE) -r -C $(TARGETDIR)/pljava \
		-f $(PROJDIR)/src/C/pljava/Makefile \
		MODULEROOT=$(PROJDIR)/src/C $@
