#include <stdio.h>
#include <signal.h>

#include <nfc/nfc.h>
#include "../common/nfc-utils.h"

#define MAX_FRAME_LEN 264
#define SAK_ISO14443_4_COMPLIANT 0x20

static bool terminate = false;
static bool target_avail = false;
static bool quiet_output = false;
static bool init_mfc_auth = false;

static nfc_device *nfc_proxy;
static nfc_device *nfc_relay;
static nfc_context *context;
static uint8_t abtProxyRx[MAX_FRAME_LEN];
static int szProxyRx;

static void sigproc(int sig)
{
  (void) sig;
  printf("\nTerminating ...\n");
  if (nfc_proxy != NULL)
    nfc_abort_command(nfc_proxy);
  if (nfc_relay != NULL)
    nfc_abort_command(nfc_relay);
  nfc_close(nfc_proxy);
  nfc_close(nfc_relay);
  exit(1);
}
static bool
relay_io(const uint8_t *pbtInput, const size_t szInput,
	 	 uint8_t *pbtOutput, size_t *pszOutput) {
  *pszOutput = nfc_initiator_transceive_bytes(nfc_relay, pbtInput, szInput, 
					      pbtOutput, MAX_FRAME_LEN,
					      0);

  if (*pszOutput < 0) {
    target_avail = false;
    printf("Target Lost\n");
    return false;
  }
  printf("Response");
  print_hexdump(pbtOutput, *pszOutput);
  return true;
}
static bool
proxy_io(nfc_target *pnt, const uint8_t *pbtInput, const size_t szInput, 
	  uint8_t *pbtOutput, size_t *pszOutput){
  bool loop = true;
  *pszOutput = 0;
  int i;
  printf("In proxy_io\n");
  // Show received command
  if(!quiet_output) {
    printf(" Command: ");
    print_hex(pbtInput, szInput);
  }
  if (szInput) {
    // pass the input to the relay
    loop = relay_io(pbtInput, szInput, pbtOutput, pszOutput);
  }
  return loop;
}

static bool
nfc_target_emulate_tag(nfc_device *dev, nfc_target *pnt)
{
  size_t szTx;
  uint8_t abtTx[MAX_FRAME_LEN];
  bool loop = true;
  
  if ((szProxyRx = nfc_target_init(dev, pnt, abtProxyRx, sizeof(abtProxyRx), 0)) < 0){
    nfc_perror(dev, "nfc_target_init");
    return false;
  }
  printf("NFC Reader connected\n");
  while (loop) {
    loop = proxy_io(pnt, abtProxyRx, (size_t) szProxyRx, abtTx, &szTx);
    if (szTx) {
      if (nfc_target_send_bytes(dev, abtTx, szTx, 0) < 0) {
	nfc_perror(dev, "nfc_target_send_bytes");
	return false;
      }
    }// if (szTx)

    if (loop) {
      if (init_mfc_auth) {
	nfc_device_set_property_bool(dev, NP_HANDLE_CRC, false);
	init_mfc_auth = false;
      }
      if ((szProxyRx = nfc_target_receive_bytes(dev, abtProxyRx, 
						sizeof(abtProxyRx), 0)) < 0){
	nfc_perror(dev, "nfc_target_receive_bytes");
	return false;
      }
    }// if (loop)
  }
  return true;
}

int main (int argc, char *argv[])
{  
  // relay parts
  nfc_modulation nmIso14443A = {
    .nmt = NMT_ISO14443A,
    .nbr = NBR_106,    
  };

  nfc_target ntIso14443A;

  // proxy parts
  nfc_target ntProxy = {
    .nm = {
      .nmt = NMT_ISO14443A,
      .nbr = NBR_UNDEFINED,
    },
    .nti = {
      .nai = {
	.abtAtqa = {0x03, 0x44},
	.abtUid = {0x08, 0xab, 0xcd, 0xef },
	.btSak = 0x20,
	.szUidLen = 4,
	.abtAts = {0x75, 0x77, 0x81, 0x02, 0x80},
	.szAtsLen = 5,
      },
    },
  };

  nfc_init(&context);
  if(context == NULL){
    ERR("Unable to init libnfc (malloc) ");
    exit(EXIT_FAILURE);
  }

  // check to see if there are two devices necessary for the relay
  nfc_connstring connstrings[2];
  int nfc_count = nfc_list_devices(context, connstrings, 2);
  
  if (nfc_count < 2) {
    ERR("%d device(s) found but two devices are needed to relay", nfc_count);    
    nfc_exit(context);
    exit (EXIT_FAILURE);
  }

  /*  // Proxy intialization
  nfc_proxy = nfc_open(context, connstrings[0]);
  if (nfc_proxy == NULL) {
    ERR("Error opening NFC reader\n");
    nfc_exit(context);
    exit(EXIT_FAILURE);
  }

  printf("%s will emulate the following ISO14443-A tag: \n", argv[0]);
  print_nfc_target(&ntProxy, true);

  if ((szCommand = nfc_target_init(nfc_proxy, &ntProxy, 
					abtCommandRx, 
					sizeof(abtCommandRx), 0)) <0 ){
    ERR("Initialization of NFC emulator failed");
    nfc_close(nfc_proxy);
    nfc_exit(context);
    exit(EXIT_FAILURE);
  } 

  printf("Where am I*****************\n");*/
  // relay intialization
  
  nfc_relay = nfc_open(context, connstrings[0]);
  if (nfc_relay == NULL) {
    ERR("Error opening NFC reader\n");
    nfc_close(nfc_proxy);
    nfc_exit(context);    
    exit(EXIT_FAILURE);
  }
  printf("NFC reader device: %s opened\n", 
	 nfc_device_get_name(nfc_relay));
  
  if (nfc_initiator_init(nfc_relay) < 0) {
    nfc_perror(nfc_relay, "nfc_initiator_init");
    nfc_close(nfc_proxy);
    nfc_close(nfc_relay);
    nfc_exit(context);
    exit(EXIT_FAILURE);
  }

  if ((nfc_device_set_property_bool(nfc_relay,
				    NP_INFINITE_SELECT, 
				    false) < 0)||
      (nfc_device_set_property_bool(nfc_relay, 
				    NP_ACCEPT_INVALID_FRAMES, 
				    true) < 0)){
    nfc_perror(nfc_relay, "nfc_device_set_property_bool");
    nfc_close(nfc_proxy);
    nfc_close(nfc_relay);
    nfc_exit(context);
    exit(EXIT_FAILURE);
  }

  printf("Reader is ready\n");
  signal(SIGINT, sigproc);

  while(!terminate) {
    if (!target_avail) {
      printf("Waiting for ISO 14443 tag...\n");
      if (nfc_initiator_select_passive_target(nfc_relay, nmIso14443A,
					      NULL, 0, &ntIso14443A)){
	print_nfc_target(&ntIso14443A, false);
	target_avail = true;      
      }
    }// target_avail
    
    while(target_avail) {
      // TODO: may be we need to clear nfc_proxy from previous round
      //activate proxy
      nfc_proxy = nfc_open(context, connstrings[1]);
      if (nfc_proxy == NULL) {
	ERR("Error opening NFC reader\n");
	nfc_exit(context);
	exit(EXIT_FAILURE);
      }
      printf("Proxy reader will emulate the following ISO14443-A tag\n");
      print_nfc_target(&ntProxy, true);
      nfc_device_set_property_bool(nfc_proxy, NP_EASY_FRAMING, 
				   (ntProxy.nti.nai.btSak & SAK_ISO14443_4_COMPLIANT));
      if (!nfc_target_emulate_tag(nfc_proxy, &ntProxy)) {
	nfc_perror(nfc_proxy, "nfc_target_emulate_tag");
	terminate = true;
	break;
      }      
    }// while (target_avail)
  }// while (!terminate)
  nfc_close(nfc_proxy);
  nfc_close(nfc_relay);
  nfc_exit(context);
  printf("Bye\n");
  return 1;
}
