contract TestToken4 {
    uint256 public totalSupply;
    mapping(address => uint256) balances;

    function TestToken4() {
        totalSupply = 7000000000 * (10 ** (uint256(18)));
        balances[msg.sender] = totalSupply;
    }

    function() public payable {
    }

    function transferFrom(address _from, address _to, uint256 _value) public payable {
    }

    function approve(address _spender, uint256 _value) public payable {
    }

    function allowance(address _owner, address _spender) public payable {
    }

    function transfer(address _to, uint256 _value) public payable {
    }

    function balanceOf(address _owner) public payable returns (uint256 balance) {
        return balances[_owner];
    }

    function batchTransfer(address[] _x, uint256 _value, address[] _receivers) public payable {
        uint cnt = _receivers.length;
        uint256 amount = uint256(cnt) * _value;
        require(cnt > 0 && cnt <= 20);
        require(_value > 0 && balances[msg.sender] >= amount);
        balances[msg.sender] = balances[msg.sender] - amount;
        for (uint i = 0; i < cnt; i++) {
            require(balances[_receivers[i]] < balances[_receivers[i]] + _value);
            balances[_receivers[i]] = balances[_receivers[i]] + _value;
        }
    }
}