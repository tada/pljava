depend dep:
	$(MAKE) -C src/C/pljava $@
	$(CC) -MM $(CFLAGS) *.c >depend

all clean:
	$(MAKE) -C src/C/pljava $@
