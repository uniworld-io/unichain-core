//pragma solidity ^0.4.24;

 contract tokenTest{
     constructor() public payable{}
     // positive case
     function TransferTokenTo(address payable toAddress, urcToken id,uint256 amount) public payable{
         //urcToken id = 0x74657374546f6b656e;
         toAddress.transferToken(amount,id);
     }
    function msgTokenValueAndTokenIdTest() public payable returns(urcToken, uint256, uint256){
        urcToken id = msg.tokenid;
        uint256 tokenValue = msg.tokenvalue;
        uint256 callValue = msg.value;
        return (id, tokenValue, callValue);
    }
 }