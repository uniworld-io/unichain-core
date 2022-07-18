contract TokenSender {
    constructor() payable public{}
    function sendURC10(address target) public payable {
        urcToken tokenId = msg.tokenid;
        bytes memory callData = abi.encodeWithSignature("receiveURC10(address,uint256,urcToken)", msg.sender, 1, tokenId);
        assembly {
            let ret := calltoken(
            gas(),
            target,
            1,
            tokenId,
            add(callData, 0x20),
            mload(callData),
            0,
            0)
            if iszero(ret) {
                revert(0, 0)
            }
        }
    }

    function sendURC10NoMethod(address target) public payable {
        urcToken tokenId = msg.tokenid;
        bytes4 sig = bytes4(keccak256("()")); // function signature
        assembly {
            let x := mload(0x40) // get empty storage location
            mstore(x,sig)
            let ret := calltoken(
            gas(),
            target,
            1, //token value
            tokenId, //token id
            x, // input
            0x04, // input size = 4 bytes
            x, // output stored at input location, save space
            0x04)
            if iszero(ret) {
                revert(0, 0)
            }
        }
    }
}

contract TokenReceiver {
    constructor() payable public{}
    event Received(address, address, uint256, urcToken);

    function receiveURC10(address origin, uint256 value, urcToken id) external payable {
        emit Received(msg.sender, origin, value, id);
    }
}