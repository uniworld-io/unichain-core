//pragma solidity ^0.4.24;

 contract tokenTest{
     constructor() public payable{}
     // positive case
     function TransferTokenTo(address toAddress, urcToken id,uint256 amount) public payable{
         //urcToken id = 0x74657374546f6b656e;
         toAddress.transferToken(amount,id);
     }
 }