export PROJDIR   := $(shell pwd)
export TARGETDIR := $(PROJDIR)/bin/build
export PGSQLDIR  := $(PROJDIR)/../pgsql

.PHONY: all clean install uninstall depend

all clean install uninstall depend:
	@mkdir -p $(TARGETDIR)/pljava
	@$(MAKE) -r -C $(TARGETDIR)/pljava \
	-f $(PROJDIR)/src/C/pljava/Makefile \
	MODULEROOT=$(PROJDIR)/src/C $@
