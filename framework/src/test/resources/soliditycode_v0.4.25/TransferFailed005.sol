contract EnergyOfTransferFailedTest {
    constructor() payable public {

    }
    function testTransferTokenCompiledLongMax() payable public{
        address(0x1).transferToken(1,9223372036855775827);
    }

    function testTransferTokenCompiledLongMin() payable public{
        //address(0x1).transferToken(1,-9223372036855775828);
    }

    function testTransferTokenCompiledLongMin1() payable public{
        address(0x1).tokenBalance(urcToken(-9223372036855775828));
    }

    function testTransferTokenCompiledLongMax1() payable public{
         address(0x1).tokenBalance(urcToken(9223372036855775827));
    }

    // InsufficientBalance
    function testTransferUnwInsufficientBalance(uint256 i) payable public{
        msg.sender.transfer(i);
    }

    function testSendUnwInsufficientBalance(uint256 i) payable public{
        msg.sender.send(i);
    }

    function testTransferTokenInsufficientBalance(uint256 i,urcToken tokenId) payable public{
        msg.sender.transferToken(i, tokenId);
    }

    function testCallUnwInsufficientBalance(uint256 i,address caller) public returns (bool){
        return caller.call.value(i)(abi.encodeWithSignature("test()"));
    }

    function testCreateUnxInsufficientBalance(uint256 i) payable public {
        (new Caller).value(i)();
    }

    // NonexistentTarget

    function testSendUnwNonexistentTarget(uint256 i,address  nonexistentTarget) payable public {
        require(address(this).balance >= i);
        nonexistentTarget.send(i);
    }

    function testTransferUnwNonexistentTarget(uint256 i,address  nonexistentTarget) payable public {
        require(address(this).balance >= i);
        nonexistentTarget.transfer(i);
    }

    function testTransferTokenNonexistentTarget(uint256 i,address  nonexistentTarget, urcToken tokenId) payable public {
        require(address(this).balance >= i);
        nonexistentTarget.transferToken(i, tokenId);
    }

    function testCallUnwNonexistentTarget(uint256 i,address  nonexistentTarget) payable public {
        require(address(this).balance >= i);
        nonexistentTarget.call.value(i)(abi.encodeWithSignature("test()"));
    }

    function testSuicideNonexistentTarget(address  nonexistentTarget) payable public {
         selfdestruct(nonexistentTarget);
    }

    // target is self
    function testTransferUnwSelf(uint256 i) payable public{
        require(address(this).balance >= i);
        address  self = address(uint160(address(this)));
        self.transfer(i);
    }

    function testSendUnwSelf(uint256 i) payable public{
        require(address(this).balance >= i);
        address  self = address(uint160(address(this)));
        self.send(i);
    }

    function testTransferTokenSelf(uint256 i,urcToken tokenId) payable public{
        require(address(this).balance >= i);
        address  self = address(uint160(address(this)));
        self.transferToken(i, tokenId);
    }

    event Deployed(address addr, uint256 salt, address sender);
        function deploy(bytes memory code, uint256 salt) public returns(address){
            address addr;
            assembly {
                addr := create2(10, add(code, 0x20), mload(code), salt)
                //if iszero(extcodesize(addr)) {
                //    revert(0, 0)
                //}
            }
            //emit Deployed(addr, salt, msg.sender);
            return addr;
        }
}



contract Caller {
    constructor() payable public {}
    function test() payable public returns (uint256 ){return 1;}
}