# Graphical Shell for long network core (cold wallet with social network and marketplace features)

Attention! The wallet does not depend on external servers! Everything you place in the blockchain stays there forever! 
All content is on your conscience! It cannot be cancelled!


# Features

* Financial transactions and wallet maintenance functions;
* Creation of channels and marketplaces;
* Creating private groups with encrypted messages;
* p2p messaging;
* Placing media content in the blockchain with automatic conversion of pictures, animations, and sounds 
  (maximum duration of voice messages up to 1 minute);
* Placement of pinned banners in groups with a lifetime limit.


# Prerequisites (for Building and Run)

- installed `longcoind` (https://github.com/longnetwork/longcoin-core) ;
- installed `openjdk-11-jdk`;
- installed `openjfx`;
- installed `ffmpeg`;

Build Process
===========================================================================================================================================

Edit the `PATH_TO_FX` environment variable in the `make.sh` file with the location of the `openjfx` library and execute that file:
```bash
./make.sh
```

Startup
===========================================================================================================================================

Edit the `PATH_TO_FX` environment variable in the `start.sh` file with the location of the `openjfx` library and execute that file:
```bash
./start.sh
```

Notes
===========================================================================================================================================

`longcoind` must be detected either by the system paths set in the `PATH` environment variable or be located in the current startup directory    
<br/>
**Pre-build 64-bit binaries (core + GUI Wallet):**
[Windows](https://drive.google.com/uc?export=download&id=1hEJbh_h8cc-b0a9PNjPMoiucILmfjWkV) | 
[Ubuntu 20.04](https://drive.google.com/uc?export=download&id=1fW4qE-30swUrB3pWo2Ee5svbmQJEjEgb) | 
[Ubuntu 18.04](https://drive.google.com/uc?export=download&id=1P4K6tRB-RtR56zK_Vj_pjse9llFK_l09)


### A donation for the development of a full-feature mobile version:
**LONG**: `1jAiYKH7yv7TWdumPNdgh6cZhuxbtGh43`  
**ETH**: `0x6e04282bb56Dd116d40785ebc3f336b4649A5bCb`  
**BNB**: `0x6e04282bb56Dd116d40785ebc3f336b4649A5bCb`  
**DOGE**: `DEBQKxDukNTToE3YvbVMFRkHBxTnUrUrTP`  
**LTC**: `LafMXhkxUp3GG1TM47GFRkmHmSGLDeCvzg`  
**BTC**: `19tAZLiVBPNVaGoxq8BTrwDqp3a41zG65b`  

