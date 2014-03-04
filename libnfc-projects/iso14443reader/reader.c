#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <signal.h>
#include <readline/readline.h>
#include <readline/history.h>
#include <time.h>

#include <nfc/nfc.h>

#include "nfc-utils.h"
#include "common-utils.h"

#define NFCREADERPROMPT "APDU> "
#define MAX_FRAME_LEN 128

static nfc_device *nfc;
static bool terminate = false;
static  nfc_context *context;

static void stop_communication(int sig)
{
  (void) sig;
  if(nfc)
    nfc_abort_command(nfc);

  nfc_close(nfc);
  nfc_exit(context);
  exit(EXIT_FAILURE);
}



void main_loop() {
  bool targetAvail = false;
  char *cmd = NULL;
  char apdu_string[MAX_FRAME_LEN];
  int i, j;
  uint8_t command[MAX_FRAME_LEN];
  uint8_t response[MAX_FRAME_LEN];
  int szResponse, szCommand;
  struct timeval tv;  
  unsigned long cmd_us_time, resp_us_time;

  nfc_modulation nmIso14443A = {
    .nmt = NMT_ISO14443A,
    .nbr = NBR_106,
  };

  nfc_target ntIso14443A;

  if(nfc_initiator_init(nfc) < 0) {
    nfc_perror(nfc, "nfc_initiator_init");
    nfc_close(nfc);
    nfc_exit(context);
    exit(EXIT_FAILURE);
  }
  
  // Device may go responseless while waiting a tag for a long time
  // Therefore, let the device only return immediately after a try
  if(nfc_device_set_property_bool(nfc, NP_INFINITE_SELECT, false) < 0) {
    nfc_perror(nfc, "nfc_device_set_property_bool");
    nfc_close(nfc);
    nfc_exit(context);
    exit(EXIT_FAILURE);
  }
  
  printf("NFC device: %s opened\n", nfc_device_get_name(nfc));
  printf("Waiting for target ...\n");

  while(!terminate) {
    if(nfc_initiator_select_passive_target(nfc, nmIso14443A,
					   NULL, 0, &ntIso14443A)){
      
      print_nfc_target(&ntIso14443A, false);
      targetAvail = true;
      
      while(targetAvail) {
	// Do all the tricks here
	cmd = readline(NFCREADERPROMPT);
	if(cmd){
	  if((strcmp(cmd, "exit") == 0)||(strcmp(cmd, "quit") == 0)){
	    terminate = true;
	    return;
	  }
	  add_history(cmd);
	  
	  // if there is space in between APDU command remove it
	  for(i = 0, j = 0; i < strlen(cmd); i++){
	    if(cmd[i] != ' '){
	      apdu_string[j] = cmd[i];
	      j++;
	    }
	  }
	  apdu_string[j] = '\0';
	  string_to_byteArray(apdu_string, command, (size_t*)&szCommand);
	  printf(">> ");
	  print_hexdump(command, szCommand);
	  gettimeofday(&tv,NULL);
	  cmd_us_time = tv.tv_sec*(uint64_t)1000000+tv.tv_usec;
	  szResponse = nfc_initiator_transceive_bytes(nfc, command, 
						      szCommand,
						      response, 
						      sizeof(response),
						      0);
	  if(szResponse < 0) {
	    targetAvail = false;
	    printf("Target Lost\n");
	    break;
	  }
	  gettimeofday(&tv,NULL);
	  resp_us_time = tv.tv_sec*(uint64_t)1000000+tv.tv_usec;
	  printf("<< ");
	  print_hexdump(response, szResponse);
	  printf("Round trip time: %lu\n", resp_us_time - cmd_us_time);					      
	}
	// if something wrong sending then terminate this session.

      }
      //terminate = true;
     printf("Waiting for target ...\n");
    }
  }// terminate
 
}

int main(int argc, char *argv[])
{
 
  nfc_init(&context);
  if(context == NULL) {
    ERR("Unable to init libnfc (malloc) ");
    exit(EXIT_FAILURE);
  }

  // Try to open the NFC device
  nfc = nfc_open(context, NULL);
  if(nfc == NULL) {
    ERR("Unable to open NFC device. \n");
    nfc_exit(context);
    exit(EXIT_FAILURE);
  }
  
  signal(SIGINT, stop_communication); //stop on interrupt
  
  // begin thread
  main_loop();

  printf("Bye Bye\n");
  nfc_close(nfc);
  nfc_exit(context);
  return 0; 
}
