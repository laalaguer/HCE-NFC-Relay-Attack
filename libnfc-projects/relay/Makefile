CC=gcc

OBJDIR = obj

INC = -I/usr/local/include/ -I/usr/include/ -I../common/
LDLIBS= -L/usr/local/lib/ -lnfc

CFLAGS = $(INC) -Wall -g

CMNSRCS = ../common/common-utils.c \
	../common/nfc-utils.c

CMNOBJS = $(CMNSRCS:%.c=%.o)
#$(info  $(CMNOBJS))

RM = rm -rf
BINS = relay
CLEAN = relay $(OBJDIR)/*.o *~

all: $(BINS)
all-static: relay

relay:$(OBJDIR)/relay.o $(CMNOBJS)
	$(CC) $^ $(LDLIBS) -o $@

$(OBJDIR)/%.o: %.c
	$(CC) $(CFLAGS) -c -o $@ $<

clean:
	$(RM) $(CLEAN)

