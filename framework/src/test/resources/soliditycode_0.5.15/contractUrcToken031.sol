//pragma solidity ^0.4.24;

 contract token{
     constructor() public payable{}

     // 4）suicide
     function kill(address payable toAddress) payable public{
         selfdestruct(toAddress);
     }

 }

contract B{
    constructor() public payable {}
    function() external payable {}
}