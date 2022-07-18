pragma solidity ^0.4.24;
contract ConvertType {

constructor() payable public{}

function() payable external{}

//function stringToUrctoken(address payable toAddress, string memory tokenStr, uint256 tokenValue) public {
// urcToken t = urcToken(tokenStr); // ERROR
// toAddress.transferToken(tokenValue, tokenStr); // ERROR
//}

function uint256ToUrctoken(address toAddress,uint256 tokenValue, uint256 tokenInt)  public {
  urcToken t = urcToken(tokenInt); // OK
  toAddress.transferToken(tokenValue, t); // OK
  toAddress.transferToken(tokenValue, tokenInt); // OK
}

function addressToUrctoken(address toAddress, uint256 tokenValue, address adr) public {
  urcToken t = urcToken(adr); // OK
  toAddress.transferToken(tokenValue, t); // OK
//toAddress.transferToken(tokenValue, adr); // ERROR
}

//function bytesToUrctoken(address payable toAddress, bytes memory b, uint256 tokenValue) public {
 // urcToken t = urcToken(b); // ERROR
 // toAddress.transferToken(tokenValue, b); // ERROR
//}

function bytes32ToUrctoken(address toAddress, uint256 tokenValue, bytes32 b32) public {
  urcToken t = urcToken(b32); // OK
  toAddress.transferToken(tokenValue, t); // OK
// toAddress.transferToken(tokenValue, b32); // ERROR
}

//function arrayToUrctoken(address payable toAddress, uint256[] memory arr, uint256 tokenValue) public {
//urcToken t = urcToken(arr); // ERROR
// toAddress.transferToken(tokenValue, arr); // ERROR
//}
}