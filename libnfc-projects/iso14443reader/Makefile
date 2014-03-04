CC=gcc

OBJDIR = obj

INC = -I/usr/local/include/ -I/usr/include/ -I../common/
LDLIBS = -L/usr/local/lib/ -lnfc -lreadline

CFLAGS = $(INC) -Wall -Wno-unused-function -g

CMNSRCS = ../common/common-utils.c \
	../common/nfc-utils.c

CMNOBJS = $(CMNSRCS:%.c=%.o)

RM = rm -rf
BINS = reader
CLEAN = reader $(OBJDIR)/*.o *~

all: $(BINS)
all-static: reader

reader:$(OBJDIR)/reader.o $(CMNOBJS)
	$(CC) $^ $(LDLIBS) -o $@

$(OBJDIR)/%.o: %.c
	$(CC) $(CFLAGS) -c -o $@ $<

clean:
	$(RM) $(CLEAN)